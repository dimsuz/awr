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
import android.view.View.OnClickListener
import advaitaworld.parsing.emptyContentInfo
import advaitaworld.util.setVisible
import android.support.v4.view.ViewCompat

/**
 * Adapter that represents a post and its comments
 */
class CommentsAdapter(val showPost: Boolean) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    class object {
        val ITEM_TYPE_CONTENT = 0
        val ITEM_TYPE_COMMENT = 1
    }

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
        val inflater = LayoutInflater.from(parent.getContext())
        return when(viewType) {
            ITEM_TYPE_CONTENT -> {
                PostViewHolder(inflater.inflate(R.layout.post_content, parent, false))
            }
            ITEM_TYPE_COMMENT -> {
                CommentViewHolder(inflater.inflate(R.layout.comment, parent, false), expandCommentAction)
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
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if(showPost && position == 0) ITEM_TYPE_CONTENT else ITEM_TYPE_COMMENT
    }

    override fun getItemCount(): Int {
        return data.size() + if(showPost) 1 else 0
    }

    /**
     * Returns an item info at the position.
     * Will throw if there's no item at this position or if item is of another type (a post content)
     */
    public fun getItemInfo(position: Int) : ItemInfo {
        val pos = if (showPost) position - 1 else position
        return data.get(pos)
    }

    trait ItemInfoHolder {
        var itemInfo : ItemInfo?
    }

    class CommentViewHolder(itemView: View, expandAction: ((CommentNode) -> Unit)?) :
            RecyclerView.ViewHolder(itemView), OnClickListener, ItemInfoHolder {

        val textView = itemView.findViewById(R.id.text) as TextView
        val expandText = itemView.findViewById(R.id.expand_comment) as TextView
        // NOTE temporarily using FrameLayout to use selectableItemBackground
        // (when this is no longer the case, just rename expandText to expandView and remove line below)
        val expandView = expandText.getParent() as View
        val expandAction: ((CommentNode) -> Unit)? = expandAction
        override var itemInfo : ItemInfo? = null

        {
            if(expandAction != null) {
                expandView.setOnClickListener(this)
            }
        }

        override fun onClick(view: View) {
            val n = itemInfo?.node
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

    setContentElevation(holder, isTopContent = true)
}

private  fun bindCommentHolder(holder: CommentViewHolder, itemInfo: ItemInfo) {
    val content = itemInfo.node.content
    holder.textView.setText("${content.author}, ${content.dateString}, ${content.rating} => ${content.text}")
    if(!itemInfo.isInStaircase && itemInfo.node.deepChildCount != 0 && holder.getPosition() != 0) {
        val count = itemInfo.node.deepChildCount
        val s = holder.expandText.getResources().getQuantityString(R.plurals.commentses, count, count)
        holder.expandView.setVisible(true)
        holder.expandText.setText(s)
    } else {
        holder.expandView.setVisible(false)
    }
    holder.itemInfo = itemInfo

    // 'staircase' of replies can appear only on top and all views in it must share the same
    // higher elevation - as they go first
    setContentElevation(holder, isTopContent = itemInfo.isInStaircase || holder.getPosition() == 0)
}

private fun setContentElevation(holder: RecyclerView.ViewHolder, isTopContent: Boolean) {
    // FIXME think of something to emulate this nicely on pre 5.0
    if(isTopContent) {
        ViewCompat.setElevation(holder.itemView, holder.itemView.getResources().getDimension(R.dimen.elevation_high))
    } else {
        ViewCompat.setElevation(holder.itemView, holder.itemView.getResources().getDimension(R.dimen.elevation_low))
    }
}

