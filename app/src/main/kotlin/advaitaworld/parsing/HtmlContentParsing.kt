package advaitaworld.parsing

import advaitaworld.R
import advaitaworld.util.isUiThread
import android.content.Context
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.text.Html
import com.squareup.picasso.Picasso
import timber.log.Timber

public interface MediaResolver {
    fun resolveImage(href: String) : Drawable
}

/**
 * Parses html content and returns a char sequence with span-styled text
 */
private fun parseHtmlContent(content: CharSequence, resolver: MediaResolver) : CharSequence {
    return Html.fromHtml(content.toString(), { href : String ->
        resolver.resolveImage(href)
    }, null)
}

public class DefaultMediaResolver(private val context: Context) : MediaResolver {
    override fun resolveImage(href: String): Drawable {
        if(isUiThread()) {
            throw RuntimeException("media parsing on UI thread! might be very slow, don't do this")
        }
        val bitmap = Picasso.with(context)
            .load(href)
            .centerInside()
            .resizeDimen(R.dimen.content_image_max_width, R.dimen.content_image_max_height)
            .onlyScaleDown()
            .get()
        //Timber.d("resolved ${bitmap.getWidth()}x${bitmap.getHeight()} for $href")
        val drawable = BitmapDrawable(context.getResources(), bitmap)
        drawable.setBounds(0, 0, bitmap.getWidth(), bitmap.getHeight())
        return drawable
    }

}
