package com.gmail.manhle240406.project.auth;

import androidx.lifecycle.SavedStateHandle;
import androidx.lifecycle.ViewModel;

public final class AuthViewModel extends ViewModel {
  private final SavedStateHandle saved;

  public AuthViewModel(SavedStateHandle saved) {
    this.saved = saved;
  }

  public boolean registering() {
    return Boolean.TRUE.equals(saved.get("registering"));
  }

  public void registering(boolean value) {
    saved.set("registering", value);
  }
}
