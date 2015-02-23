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
        if(node.children != null) {
            showChildren(node.children)
        }
    }

    private fun showChildren(children: List<CommentNode>) {
        for(child in children) {
            val view = CommentView(getContext())
            view.showComment(child)
            val params = createMatchWrapLayoutParams()
            params.leftMargin = 20
            addView(view, params)
        }
    }
}

private fun createMatchWrapLayoutParams(): MarginLayoutParams {
    return LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
}
