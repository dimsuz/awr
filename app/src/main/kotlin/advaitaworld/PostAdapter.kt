package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.LayoutInflater
import advaitaworld.parsing.PostData
import android.view.ViewGroup
import advaitaworld.parsing.ContentInfo
import advaitaworld.PostAdapter.PostViewHolder
import advaitaworld.parsing.CommentNode
import advaitaworld.PostAdapter.CommentViewHolder
import android.widget.TextView
import advaitaworld.views.CommentTreeView
import android.view.ViewGroup.LayoutParams

private val ITEM_TYPE_CONTENT = 0
private val ITEM_TYPE_COMMENT = 1

/**
 * Adapter that represents a post and its comments
 */
class PostAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    var data: PostData? = null

    public fun swapData(data: PostData) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        if(viewType == ITEM_TYPE_COMMENT) {
            val view = CommentTreeView(parent.getContext())
            parent.addView(view,
                    ViewGroup.LayoutParams(LayoutParams.MATCH_PARENT,  LayoutParams.WRAP_CONTENT))
            return CommentViewHolder(view)
        } else {
            val inflater = LayoutInflater.from(parent.getContext())
            return PostViewHolder(inflater.inflate(R.layout.post_content, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if(getItemViewType(position) == ITEM_TYPE_CONTENT) {
            bindPostHolder(holder as PostViewHolder, data!!.content)
        } else {
            bindCommentHolder(holder as CommentViewHolder, data!!.comments.get(position - 1))
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if(position == 0) ITEM_TYPE_CONTENT else ITEM_TYPE_COMMENT
    }

    override fun getItemCount(): Int {
        return if(data != null) data!!.comments.size() + 1 else 0
    }

    class CommentViewHolder(itemView: CommentTreeView) : RecyclerView.ViewHolder(itemView) {
        val commentView = itemView
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
