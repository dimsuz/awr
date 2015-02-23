package advaitaworld.views

import android.widget.FrameLayout
import android.content.Context
import advaitaworld.R
import android.view.LayoutInflater
import android.widget.TextView
import android.view.ViewGroup.LayoutParams
import advaitaworld.parsing.CommentNode

public class CommentView(context: Context) : FrameLayout(context) {
    val text: TextView
    {
        LayoutInflater.from(context).inflate(R.layout.comment, this, true)
        text = findViewById(R.id.text) as TextView
        text.setLayoutParams(FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
    }

    public fun showComment(comment: CommentNode) {
        val content = comment.content
        text.setText("${content.author}, ${content.dateString}, ${content.rating}, ${comment.deepChildCount} => ${content.text}")
    }
}

