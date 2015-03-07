package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.View
import advaitaworld.parsing.ContentInfo
import advaitaworld.parsing.CommentNode
import android.widget.TextView
import android.view.ViewGroup
import android.view.LayoutInflater
import advaitaworld.CommentsAdapter.PostViewHolder
import advaitaworld.CommentsAdapter.CommentViewHolder
import advaitaworld.views.CommentView
import android.view.View.OnClickListener
import advaitaworld.parsing.emptyContentInfo
import advaitaworld.CommentsAdapter.ExpandViewHolder

/**
 * Adapter that represents a post and its comments
 */
class CommentsAdapter(val showPost: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    private val ITEM_TYPE_CONTENT = 0
    private val ITEM_TYPE_COMMENT = 1
    private val ITEM_TYPE_COMMENT_EXPAND = 2

    private var expandCommentAction: ((CommentNode) -> Unit)? = null
    private var data: List<ItemInfo> = listOf()
    private var postContent: ContentInfo = emptyContentInfo()

    public fun swapData(postContent: ContentInfo, data: List<ItemInfo>) {
        this.postContent = postContent
        this.data = data
        notifyDataSetChanged()
    }

    fun getData(): List<ItemInfo> {
        return data
    }

    public fun setExpandCommentAction(action: (CommentNode) -> Unit) {
        expandCommentAction = action
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return when(viewType) {
            ITEM_TYPE_CONTENT -> {
                val inflater = LayoutInflater.from(parent.getContext())
                PostViewHolder(inflater.inflate(R.layout.post_content, parent, false))
            }
            ITEM_TYPE_COMMENT -> {
                val view = CommentView(parent.getContext())
                CommentViewHolder(view)
            }
            ITEM_TYPE_COMMENT_EXPAND -> {
                val inflater = LayoutInflater.from(parent.getContext())
                ExpandViewHolder(inflater.inflate(R.layout.comment_expand, parent, false), expandCommentAction)
            }
            else -> throw RuntimeException("unknown view type")
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when(getItemViewType(position)) {
            ITEM_TYPE_CONTENT -> bindPostHolder(holder as PostViewHolder, postContent)
            ITEM_TYPE_COMMENT -> {
                val pos = if (showPost) position - 1 else position
                bindCommentHolder(holder as CommentViewHolder, data.get(pos))
            }
            ITEM_TYPE_COMMENT_EXPAND -> {
                val pos = if (showPost) position - 1 else position
                bindCommentExpandHolder(holder as ExpandViewHolder, data.get(pos))
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        val commentPos = if(showPost) position - 1 else position
        return if(showPost && position == 0) ITEM_TYPE_CONTENT else
            (when(data.get(commentPos).type) {
                ItemType.Top, ItemType.Reply -> ITEM_TYPE_COMMENT
                ItemType.Expand -> ITEM_TYPE_COMMENT_EXPAND
            })
    }

    override fun getItemCount(): Int {
        return data.size() + if(showPost) 1 else 0
    }

    class CommentViewHolder(val commentView: CommentView) : RecyclerView.ViewHolder(commentView) {

    }

    class ExpandViewHolder(val expandView: View,
                           val expandAction: ((CommentNode) -> Unit)?) : RecyclerView.ViewHolder(expandView), OnClickListener {
        val textView = expandView.findViewById(R.id.text_expand) as TextView
        var node: CommentNode? = null

        {
            if(expandAction != null) {
                expandView.setOnClickListener(this)
            }
        }

        override fun onClick(view: View) {
            val n = node
            val action = expandAction
            if(n != null && action != null) {
                action(n)
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
    // FIXME extract post title
    holder.titleView.setText("Вот те раз!")
    holder.subtitleView.setText(content.author)
    holder.contentView.setText(content.text)
}

private  fun bindCommentHolder(holder: CommentViewHolder, itemInfo: ItemInfo) {
    holder.commentView.showComment(itemInfo.node, itemInfo.indentLevel > 0)
}

private  fun bindCommentExpandHolder(holder: ExpandViewHolder, itemInfo: ItemInfo) {
    holder.textView.setText("+${itemInfo.node.deepChildCount} комментариев")
    holder.node = itemInfo.node
}

