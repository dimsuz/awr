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
    val inflater: LayoutInflater
    var expandAction: ((CommentNode) -> Unit)? = null

    {
        setOrientation(LinearLayout.VERTICAL)

        inflater = LayoutInflater.from(context)
    }

    public fun showCommentTree(node: CommentNode) {
        // FIXME optimize to remove only unused views
        removeAllViews()

        val layoutParamsLevel0 = createLayoutParams(leftMargin = 0)
        val layoutParamsLevel1 = createLayoutParams(leftMargin = 20)
        val view = CommentView(getContext())
        view.showComment(node, false)
        addView(view, layoutParamsLevel0)
        var n = node
        while(n.children.size() == 1) {
            val child = n.children.first()
            val view = CommentView(getContext())
            view.showComment(child, true)
            addView(view, layoutParamsLevel0)
            n = child
        }

        for(child in n.children) {
            val view = CommentView(getContext())
            view.showComment(child, false)
            addView(view, layoutParamsLevel1)
            if(!child.children.isEmpty()) {
                addView(createExpandView(child, 20))
            }
        }
    }

    public fun setExpandCommentAction(action: (CommentNode) -> Unit) {
        expandAction = action
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
