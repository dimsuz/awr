package advaitaworld.views

import android.widget.LinearLayout
import android.content.Context
import advaitaworld.parsing.CommentNode
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams

public class CommentTreeView(context: Context) : LinearLayout(context) {
    val rootView: CommentView

    {
        setOrientation(LinearLayout.VERTICAL)

        rootView = CommentView(context)
        addView(rootView, createMatchWrapLayoutParams())
    }

    public fun showCommentTree(node: CommentNode) {
        // FIXME optimize to remove only unused views
        removeAllViews()
        addView(rootView, createMatchWrapLayoutParams())

        rootView.showComment(node)
        showChildren(node, level = 0)
    }

    private fun showChildren(node: CommentNode, level: Int) {
        if(node.children == null || level > MAX_COMMENT_LEVEL) {
            return
        }
        for(child in node.children) {
            val view = CommentView(getContext())
            view.showComment(child)
            val params = createMatchWrapLayoutParams()
            params.leftMargin = 20 * (level + 1)
            addView(view, params)
            showChildren(child, level + 1)
        }
    }
}

private val MAX_COMMENT_LEVEL = 2

private fun createMatchWrapLayoutParams(): MarginLayoutParams {
    return LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}
