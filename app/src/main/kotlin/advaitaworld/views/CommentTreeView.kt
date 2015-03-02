package advaitaworld.views

import android.widget.LinearLayout
import android.content.Context
import advaitaworld.parsing.CommentNode
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.view.LayoutInflater
import advaitaworld.R
import android.view.ViewGroup
import android.widget.TextView
import android.view.View

public class CommentTreeView(context: Context) : LinearLayout(context) {
    val rootView: CommentView
    val inflater: LayoutInflater
    var expandAction: ((CommentNode) -> Unit)? = null

    {
        setOrientation(LinearLayout.VERTICAL)

        rootView = CommentView(context)
        inflater = LayoutInflater.from(context)
        addView(rootView, createLayoutParams(leftMargin = 0))
    }

    public fun showCommentTree(node: CommentNode) {
        // FIXME optimize to remove only unused views
        removeAllViews()
        addView(rootView, createLayoutParams(leftMargin = 0))

        rootView.showComment(node)
        showChildren(node, level = 0)
    }

    public fun setExpandCommentAction(action: (CommentNode) -> Unit) {
        expandAction = action
    }

    private fun showChildren(node: CommentNode, level: Int) {
        val layoutParams = createLayoutParams(leftMargin = 20 * (level + 1))
        for(child in node.children) {
            val view = CommentView(getContext())
            view.showComment(child)
            addView(view, layoutParams)
            if(level < MAX_COMMENT_LEVEL) {
                showChildren(child, level + 1)
            } else if(!child.children.isEmpty()) {
                addView(createExpandView(child, layoutParams.leftMargin))
            }
        }
    }

    private fun createExpandView(child: CommentNode, levelMargin: Int) : View {
        val expandItem = inflater.inflate(R.layout.comment_expand, null) as ViewGroup
        val expandView = expandItem.getChildAt(0) as TextView
        expandView.setText("+${child.deepChildCount} комментариев")
        expandItem.setOnClickListener { if(expandAction != null) expandAction!!(child) }
        val extraMargin = getResources().getDimensionPixelSize(R.dimen.margin)
        expandItem.setLayoutParams(createLayoutParams(levelMargin + extraMargin))
        return expandItem
    }
}

private val MAX_COMMENT_LEVEL = 2

private fun createLayoutParams(leftMargin: Int): MarginLayoutParams {
    val params = LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
    params.leftMargin = leftMargin
    return params
}
