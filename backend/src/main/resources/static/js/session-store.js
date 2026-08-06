const PROFILE_KEY = "smartqueue.profile";
const LEGACY_AUTH_KEY = "smartqueue.auth";

export function storeProfile(auth) {
  if (auth)
    localStorage.setItem(
      PROFILE_KEY,
      JSON.stringify({
        email: auth.email,
        role: auth.role,
        userId: auth.userId,
      }),
    );
}

export const hasStoredProfile = () =>
  Boolean(
    localStorage.getItem(PROFILE_KEY) || localStorage.getItem(LEGACY_AUTH_KEY),
  );
export const removeLegacyAccessToken = () =>
  localStorage.removeItem(LEGACY_AUTH_KEY);
export function clearStoredSession() {
  localStorage.removeItem(PROFILE_KEY);
  localStorage.removeItem(LEGACY_AUTH_KEY);
}
