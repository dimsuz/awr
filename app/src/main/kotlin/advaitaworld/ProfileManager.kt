package advaitaworld

import advaitaworld.parsing.ProfileInfo
import android.content.Context
import android.preference.PreferenceManager

/**
 * Saves a profile of the logged in user, sets it as current
 */
public fun setCurrentUserProfile(context: Context, profileInfo: ProfileInfo) {
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    prefs.edit()
        .putString(PREF_KEY_LAST_LOGIN_NAME, profileInfo.name)
        .putString(PREF_KEY_LAST_LOGIN_EMAIL, profileInfo.email)
        .putString(PREF_KEY_LAST_LOGIN_IMAGE_URL, profileInfo.pictureUrl)
        .apply()
}

/**
 * Returns either a logged in user profile or null if no user is logged in
 */
public fun getCurrentUserProfile(context: Context, server: Server) : ProfileInfo? {
    if(!server.isLoggedIn()) return null

    val prefs = PreferenceManager.getDefaultSharedPreferences(context)
    val name = prefs.getString(PREF_KEY_LAST_LOGIN_NAME, null)
    val email = prefs.getString(PREF_KEY_LAST_LOGIN_EMAIL, null)
    val pictureUrl = prefs.getString(PREF_KEY_LAST_LOGIN_IMAGE_URL, null)
    if(name != null && email != null && pictureUrl != null) {
        return ProfileInfo(name, email, pictureUrl)
    } else {
        throw RuntimeException("profile info data missing, expected not null name=$name, email=$email, url=$pictureUrl")
    }
}

