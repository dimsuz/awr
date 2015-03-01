package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.View
import advaitaworld.parsing.PostData
import advaitaworld.parsing.ContentInfo
import advaitaworld.parsing.CommentNode
import android.widget.TextView
import timber.log.Timber
import android.content.Intent
import android.view.ViewGroup
import advaitaworld.views.CommentTreeView
import android.view.ViewGroup.LayoutParams
import android.view.LayoutInflater
import advaitaworld.CommentsAdapter.PostViewHolder
import advaitaworld.CommentsAdapter.CommentViewHolder

private val ITEM_TYPE_CONTENT = 0
private val ITEM_TYPE_COMMENT = 1

/**
 * Adapter that represents a post and its comments
 */
class CommentsAdapter(val showPost: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private var expandCommentAction: ((CommentNode) -> Unit)? = null
    private var data: PostData? = null

    public fun swapData(data: PostData) {
        this.data = data
        notifyDataSetChanged()
    }

    fun getData(): PostData? {
        return data
    }

    public fun setExpandCommentAction(action: (CommentNode) -> Unit) {
        expandCommentAction = action
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if(viewType == ITEM_TYPE_COMMENT) {
            val view = CommentTreeView(parent.getContext())
            parent.addView(view,
                    ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,  LayoutParams.WRAP_CONTENT))
            return CommentViewHolder(view, expandCommentAction)
        } else {
            val inflater = LayoutInflater.from(parent.getContext())
            return PostViewHolder(inflater.inflate(R.layout.post_content, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if(getItemViewType(position) == ITEM_TYPE_CONTENT) {
            bindPostHolder(holder as PostViewHolder, data!!.content)
        } else {
            val comment = data!!.comments.get(if(showPost) position - 1 else position)
            bindCommentHolder(holder as CommentViewHolder, comment)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if(showPost && position == 0) ITEM_TYPE_CONTENT else ITEM_TYPE_COMMENT
    }

    override fun getItemCount(): Int {
        val adjust = if(showPost) 1 else 0
        return if(data != null) data!!.comments.size() + adjust else 0
    }

    class CommentViewHolder(val commentView: CommentTreeView, val expandAction: ((CommentNode) -> Unit)?) : RecyclerView.ViewHolder(commentView) {
        {
            if(expandAction != null) {
                commentView.setExpandCommentAction(expandAction)
            }
        }
    }

    class PostViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleView = itemView.findViewById(R.id.post_title) as TextView
        val subtitleView = itemView.findViewById(R.id.post_subtitle) as TextView
        val contentView = itemView.findViewById(R.id.post_content) as TextView
    }
}

private fun bindPostHolder(holder: PostViewHolder, content: ContentInfo) {
    holder.titleView.setText("Вот те раз!")
    holder.subtitleView.setText(content.author)
    holder.contentView.setText(content.text)
}

private  fun bindCommentHolder(holder: CommentViewHolder, comment: CommentNode) {
    holder.commentView.showCommentTree(comment)
}
