package advaitaworld.db

import android.provider.BaseColumns
import rx.Observable
import android.graphics.Bitmap
import com.squareup.picasso.Picasso
import android.content.Context
import android.net.Uri
import com.squareup.picasso.Target
import android.graphics.drawable.Drawable
import rx.subscriptions.Subscriptions
import rx.Subscriber
import timber.log.Timber
import android.widget.ImageView
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers

public val USERS_TABLE: String = "users"
public val USERS_COLUMN_ID: String = BaseColumns._ID
public val USERS_COLUMN_NAME: String = "name"
public val USERS_COLUMN_AVATAR_URL: String = "ava_url"
