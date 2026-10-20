package com.gmail.manhle240406.project.pet;

import android.app.Application;
import android.os.*;
import androidx.annotation.NonNull;
import androidx.lifecycle.*;
import com.gmail.manhle240406.project.AniloveApplication;
import com.gmail.manhle240406.project.foundation.*;
import com.google.firebase.firestore.FirebaseFirestore;
import java.util.*;

/** Private input survives rotation only. SavedState stores operation identifiers/bases only. */
public final class PetViewModel extends AndroidViewModel {
  public final MutableLiveData<Long> changes = new MutableLiveData<>(0L);
  public final Map<String, String> text = new HashMap<>();
  public int sex, basis;
  public boolean marksUnknown = true,
      care,
      busy,
      lost,
      restored,
      editingNew,
      committed,
      challengeMode;
  public PetIdentityRepository.Detail detail, conflict;
  public PetIdentityRepository.Page page;
  public String pageKind = "pets", petId;
  public PetCommand pending, conflictCommand;
  public PetIdentityRepository.Code code = PetIdentityRepository.Code.SERVER;
  public long formRevision, conflictRevision;
  private final SavedStateHandle saved;
  private final SessionGateway sessions;
  private final SessionEpoch.Ticket ticket;
  private final PetIdentityRepository repository;
  private final Handler main = new Handler(Looper.getMainLooper());
  private final Runnable invalidation = this::clear;
  private long generation, event;

  public PetViewModel(@NonNull Application app, SavedStateHandle saved) {
    super(app);
    this.saved = saved;
    FirebaseEnvironment e = ((AniloveApplication) app).foundation();
    sessions = e.sessions;
    ticket = sessions == null ? null : sessions.capture();
    repository =
        sessions == null
            ? null
            : new PetIdentityRepository(FirebaseFirestore.getInstance(), sessions);
    if (sessions != null) sessions.onInvalidated(invalidation);
    if (ticket == null || ticket.uid == null) {
      clear();
      return;
    }
    String scope = saved.get("pet_operation_uid");
    if (scope != null && !scope.equals(ticket.uid)) clearSaved();
    restored = saved.get("pet_operation_id") != null;
    if (restored) {
      petId = saved.get("pet_operation_target");
      changed();
    } else list("pets", false);
  }

  private void changed() {
    changes.setValue(++event);
  }

  private boolean current(long g) {
    return !lost && g == generation && sessions.current(ticket);
  }

  private void clearSaved() {
    for (String key :
        new String[] {
          "pet_operation_uid",
          "pet_operation_id",
          "pet_operation_target",
          "pet_operation_type",
          "pet_base_identity",
          "pet_base_claim",
          "pet_base_authority",
          "pet_base_private"
        }) saved.remove(key);
  }

  private void clear() {
    generation++;
    clearSaved();
    text.clear();
    detail = null;
    conflict = null;
    pending = null;
    conflictCommand = null;
    page = null;
    petId = null;
    busy = false;
    lost = true;
    changed();
  }

  public void remember(PetCommand c) {
    saved.set("pet_operation_uid", ticket.uid);
    saved.set("pet_operation_id", c.operationId);
    saved.set("pet_operation_target", c.petId);
    saved.set("pet_operation_type", c.type);
    saved.set("pet_base_identity", c.identityVersion);
    saved.set("pet_base_claim", c.claimVersion);
    saved.set("pet_base_authority", c.authorityVersion);
    saved.set("pet_base_private", c.privateVersion);
  }

  public void reconcile() {
    if (busy || lost) return;
    String op = saved.get("pet_operation_id");
    if (op == null) return;

    busy = true;
    long g = generation;
    changed();
    repository
        .reconcile(ticket, op)
        .addOnSuccessListener(
            r -> {
              if (!current(g)) return;
              busy = false;
              code = r.code;
              if (r.code == PetIdentityRepository.Code.SERVER) {
                boolean matches =
                    Objects.equals(saved.get("pet_operation_type"), r.value.get("type"))
                        && Objects.equals(saved.get("pet_operation_target"), r.value.get("petId"))
                        && Objects.equals(
                            saved.get("pet_base_identity"), r.value.get("baseIdentityVersion"))
                        && Objects.equals(
                            saved.get("pet_base_claim"), r.value.get("baseClaimVersion"))
                        && Objects.equals(
                            saved.get("pet_base_authority"), r.value.get("baseAuthorityVersion"));
                if (!matches) {
                  code = PetIdentityRepository.Code.CONFLICT;
                  changed();
                  return;
                }
                restored = false;
                clearSaved();
                pending = null;
                committed = true;
                if (!"submit_challenge".equals(r.value.get("type"))
                    && !"suspend_offering".equals(r.value.get("type")))
                  select((String) r.value.get("petId"));
                else
                  list(
                      "submit_challenge".equals(r.value.get("type")) ? "challenges" : "receipts",
                      false);
              }
              changed();
            });
  }

  public void list(String kind, boolean next) {
    if (busy || lost || pending != null) return;
    busy = true;
    long g = generation;
    changed();
    repository
        .list(ticket, kind, next && kind.equals(pageKind) && page != null ? page.cursor : null)
        .addOnSuccessListener(
            r -> {
              if (!current(g)) return;
              busy = false;
              code = r.code;
              if (r.code == PetIdentityRepository.Code.SERVER) {
                page = r.value;
                pageKind = kind;
              }
              changed();
            });
  }

  public void newPet() {
    if (busy || pending != null || restored || page == null || !"pets".equals(pageKind)) return;
    text.clear();
    sex = 0;
    basis = 0;
    marksUnknown = true;
    care = false;
    detail = null;
    petId = UUID.randomUUID().toString();
    editingNew = true;
    conflict = null;
    committed = false;
    formRevision++;
    changed();
  }

  public void releaseRestored() {
    if (restored && !busy && pending == null && page != null && "pets".equals(pageKind)) {
      clearSaved();
      restored = false;
      committed = false;
      changed();
    }
  }

  public void suspendKnown(String id) {
    if (busy || lost || restored || pending != null || conflict != null) return;
    busy = true;
    long g = generation;
    changed();
    repository
        .ownAuthority(ticket, id)
        .addOnSuccessListener(
            r -> {
              if (!current(g)) return;
              busy = false;
              code = r.code;
              if (r.code == PetIdentityRepository.Code.SERVER) {
                Map<String, Object> a = r.value;
                execute(
                    new PetCommand(
                        "suspend_offering",
                        id,
                        UUID.randomUUID().toString(),
                        PetSchema.version(a, "identityVersion"),
                        PetSchema.version(a, "claimVersion"),
                        PetSchema.version(a, "authorityVersion"),
                        0,
                        Collections.emptyMap()));
              }
              changed();
            });
  }

  public void select(String id) {
    if (busy || lost || pending != null) return;
    petId = id;
    busy = true;
    editingNew = false;
    long g = generation;
    changed();
    repository
        .load(ticket, id)
        .addOnSuccessListener(
            r -> {
              if (!current(g)) return;
              busy = false;
              code = r.code;
              if (r.code == PetIdentityRepository.Code.SERVER) {
                detail = r.value;
                fill();
              }
              changed();
            });
  }

  private void fill() {
    Map<String, Object> i = PetSchema.nested(detail.revision, "identity"), c = detail.claim;
    for (String key : new String[] {"displayName", "species", "breed", "marks"})
      text.put(key, (String) i.get(key));
    sex = PetSchema.SEXES.indexOf(i.get("sex"));
    marksUnknown = (Boolean) i.get("marksUnknown");
    basis = PetSchema.BASES.indexOf(c.get("basis"));
    care = (Boolean) c.get("reportedCare");
    text.put("explanation", (String) c.get("explanation"));
    text.put("ageMonths", i.get("ageMonths") == null ? "" : String.valueOf(i.get("ageMonths")));
    text.put(
        "ageObservedAt",
        i.get("ageObservedAt") == null
            ? ""
            : ((com.google.firebase.Timestamp) i.get("ageObservedAt"))
                .toDate()
                .toInstant()
                .atZone(java.time.ZoneOffset.UTC)
                .toLocalDate()
                .toString());
    formRevision++;
  }

  public void execute(PetCommand c) {
    if (busy || lost || restored || conflict != null || pending != null && pending != c) return;
    pending = c;
    remember(c);
    busy = true;
    committed = false;
    code = PetIdentityRepository.Code.SERVER;
    long g = generation;
    changed();
    Runnable timeout =
        () -> {
          if (current(g) && busy && pending == c) {
            code = PetIdentityRepository.Code.UNKNOWN_OUTCOME;
            changed();
          }
        };
    main.postDelayed(timeout, 15000);
    repository
        .execute(ticket, c)
        .addOnSuccessListener(
            r -> {
              main.removeCallbacks(timeout);
              if (!current(g)) return;
              busy = false;
              code = r.code;
              if (r.code == PetIdentityRepository.Code.COMMITTED) {
                pending = null;
                clearSaved();
                committed = true;
                if ("submit_challenge".equals(c.type)) {
                  text.remove("statement");
                  formRevision++;
                } else if ("suspend_offering".equals(c.type)) {
                  detail = null;
                  editingNew = false;
                } else select(c.petId);
              } else if (r.code == PetIdentityRepository.Code.CONFLICT) {
                conflictCommand = c;
                pending = null;
                clearSaved();
                busy = true;
                repository
                    .load(ticket, c.petId)
                    .addOnSuccessListener(
                        fresh -> {
                          if (!current(g)) return;
                          busy = false;
                          if (fresh.code == PetIdentityRepository.Code.SERVER) {
                            conflict = fresh.value;
                            conflictRevision++;
                          }
                          code =
                              fresh.code == PetIdentityRepository.Code.SERVER
                                  ? PetIdentityRepository.Code.CONFLICT
                                  : fresh.code;
                          changed();
                        });
              } else if (r.code != PetIdentityRepository.Code.UNKNOWN_OUTCOME) {
                pending = null;
                clearSaved();
              }
              changed();
            });
  }

  public void resolve(boolean keep) {
    if (conflict == null) return;
    detail = conflict;
    conflict = null;
    conflictCommand = null;
    code = PetIdentityRepository.Code.SERVER;
    if (!keep) fill();
    changed();
  }

  public long identityVersion() {
    return detail == null ? 0 : PetSchema.version(detail.pet, "identityVersion");
  }

  public long claimVersion() {
    return detail == null ? 0 : PetSchema.version(detail.claim, "claimVersion");
  }

  public long authorityVersion() {
    return detail == null ? 0 : PetSchema.version(detail.authority, "authorityVersion");
  }

  public long privateVersion() {
    return 0;
  }

  @Override
  protected void onCleared() {
    generation++;
    main.removeCallbacksAndMessages(null);
    if (sessions != null) sessions.removeInvalidation(invalidation);
    text.clear();
    detail = null;
    conflict = null;
    pending = null;
    page = null;
  }
}
