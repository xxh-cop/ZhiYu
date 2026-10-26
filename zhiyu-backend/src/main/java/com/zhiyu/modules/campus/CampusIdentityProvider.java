package com.zhiyu.modules.campus;

/** Future SSO adapters must validate school-signed assertions server-side. No unverified claims grant roles. */
public interface CampusIdentityProvider {
    VerifiedIdentity verify(String authorizationCode, String redirectUri);
    record VerifiedIdentity(String institution, String subject, String college) {}
}
