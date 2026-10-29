package com.gmail.manhle240406.project.adoption;

import android.view.*;
import android.widget.*;

import androidx.recyclerview.widget.RecyclerView;

import com.gmail.manhle240406.project.R;
import com.gmail.manhle240406.project.community.SocialViews;

import java.util.*;
import java.util.function.Consumer;

/** Recycling saved rows avoids retaining a view hierarchy for every saved item. */
final class FavoriteRows extends RecyclerView.Adapter<FavoriteRows.Holder> {
    static final class Row {
        final String id;
        ListingRow source;
        boolean resolved;

        Row(String id) {
            this.id = id;
        }
    }

    final List<Row> rows = new ArrayList<>();
    boolean busy, end, mutation, failed;
    private final android.content.Context context;
    private final Consumer<Row> open, remove;
    private final Runnable more;

    FavoriteRows(android.content.Context c, Consumer<Row> o, Consumer<Row> r, Runnable m) {
        context = c;
        open = o;
        remove = r;
        more = m;
    }

    public int getItemCount() {
        return rows.size() + 1;
    }

    public int getItemViewType(int p) {
        return p == rows.size() ? 1 : 0;
    }

    public Holder onCreateViewHolder(ViewGroup parent, int type) {
        if (type == 1) {
            LinearLayout col = SocialViews.column(context);
            col.setLayoutParams(new RecyclerView.LayoutParams(-1, -2));
            col.addView(SocialViews.button(context, context.getString(R.string.wiring_more), more));
            return new Holder(col);
        }
        return new Holder(
                LayoutInflater.from(context).inflate(R.layout.item_pet_discovery, parent, false));
    }

    public void onBindViewHolder(Holder h, int p) {
        if (p == rows.size()) {
            LinearLayout col = (LinearLayout) h.itemView;
            h.itemView.setVisibility(
                    rows.isEmpty() && (busy || end && !failed) ? View.GONE : View.VISIBLE);
            TextView button = (TextView) col.getChildAt(0);
            button.setText(
                    busy
                            ? R.string.wiring_loading
                            : failed
                                    ? R.string.wiring_retry
                                    : rows.isEmpty()
                                            ? R.string.wiring_empty
                                            : end
                                                    ? R.string.feed_listing_end
                                                    : R.string.wiring_more);
            button.setEnabled(!busy && !end || failed);
            return;
        }
        Row row = rows.get(p);
        TextView name = h.itemView.findViewById(R.id.pet_name),
                details = h.itemView.findViewById(R.id.pet_breed_age),
                region = h.itemView.findViewById(R.id.pet_region);
        ImageView image = h.itemView.findViewById(R.id.pet_image),
                heart = h.itemView.findViewById(R.id.pet_favorite_btn);
        if (!row.resolved) {
            name.setText(R.string.wiring_loading);
            details.setText("");
            region.setText("");
            image.setImageResource(R.drawable.ic_pet_paw_vector);
        } else if (row.source == null) {
            name.setText(R.string.wiring_unavailable_item);
            details.setText(R.string.ui_saved_private);
            region.setText("");
            image.setImageResource(R.drawable.ic_pet_paw_vector);
        } else {
            name.setText(row.source.getString("petName"));
            details.setText(row.source.getString("title"));
            region.setText(row.source.getString("regionLabel"));
            String illustration = row.source.getString("bundledIllustrationKey");
            image.setImageResource(
                    "cat".equals(illustration)
                            ? R.drawable.profile_avatar_cat
                            : "dog".equals(illustration)
                                    ? R.drawable.profile_avatar_dog
                                    : R.drawable.ic_pet_paw_vector);
        }
        h.itemView.setOnClickListener(row.source == null ? null : v -> open.accept(row));
        h.itemView.setEnabled(row.source != null);
        heart.setImageResource(R.drawable.ic_nav_favorite);
        heart.setContentDescription(context.getString(R.string.wiring_remove_saved));
        heart.setEnabled(!mutation);
        heart.setOnClickListener(v -> remove.accept(row));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
