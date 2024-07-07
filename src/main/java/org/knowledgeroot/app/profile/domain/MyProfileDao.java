package org.knowledgeroot.app.profile.domain;

public interface MyProfileDao {
    /**
     * find my profile
     * @return
     */
    MyProfile findMyProfile();

    /**
     * update my profile
     * @param myProfile
     */
    void updateMyProfile(MyProfile myProfile);
}
