package advaitaworld.auth

import advaitaworld.*
import advaitaworld.parsing.ProfileInfo
import android.content.Context
import android.preference.PreferenceManager
import rx.Observable

public class ProfileManager(private val server: advaitaworld.net.Server) {
    /**
     * Logs the user and performs some additional common post-login actions
     */
    public fun loginUser(context: Context, userLogin: String, password: String) : Observable<ProfileInfo> {
        return server.loginUser(userLogin, password)
            .doOnNext({ profile -> setCurrentUserProfile(context, profile) })
    }

    /**
     * Logs out specified user and performs additional clean up
     */
    public fun logoutUser(context: Context, profileInfo: ProfileInfo) : Observable<ProfileInfo> {
        return server.logoutUser(profileInfo)
            .map { profileInfo }
            .doOnNext({ removeLoggedUserProfile(context) })
    }

    /**
     * Saves a profile of the logged in user, sets it as current
     */
    private fun setCurrentUserProfile(context: Context, profileInfo: ProfileInfo) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .putString(PREF_KEY_LAST_LOGIN_NAME, profileInfo.name)
            .putString(PREF_KEY_LAST_LOGIN_EMAIL, profileInfo.email)
            .putString(PREF_KEY_LAST_LOGIN_IMAGE_URL, profileInfo.pictureUrl)
            .putString(PREF_KEY_LAST_LOGIN_SECURITY_KEY, profileInfo.securityKey)
            .apply()
    }

    /**
     * Removes a last saved user profile information
     */
    private fun removeLoggedUserProfile(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        prefs.edit()
            .remove(PREF_KEY_LAST_LOGIN_NAME)
            .remove(PREF_KEY_LAST_LOGIN_EMAIL)
            .remove(PREF_KEY_LAST_LOGIN_IMAGE_URL)
            .remove(PREF_KEY_LAST_LOGIN_SECURITY_KEY)
            .apply()
    }

    /**
     * Returns either a logged in user profile or null if no user is logged in
     */
    public fun getCurrentUserProfile(context: Context) : ProfileInfo? {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val name = prefs.getString(PREF_KEY_LAST_LOGIN_NAME, null)
        val email = prefs.getString(PREF_KEY_LAST_LOGIN_EMAIL, null)
        val pictureUrl = prefs.getString(PREF_KEY_LAST_LOGIN_IMAGE_URL, null)
        val securityKey = prefs.getString(PREF_KEY_LAST_LOGIN_SECURITY_KEY, null)
        if(name != null && email != null && pictureUrl != null && securityKey != null) {
            return ProfileInfo(name, email, pictureUrl, securityKey)
        } else {
            return null
        }
    }
}