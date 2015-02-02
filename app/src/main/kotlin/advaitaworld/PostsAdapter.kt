package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.view.LayoutInflater
import android.net.Uri
import com.squareup.picasso.Picasso
import advaitaworld.PostsAdapter.ViewHolder
import advaitaworld.db.User
import timber.log.Timber

public class PostsAdapter() : RecyclerView.Adapter<ViewHolder>() {
    var data: List<Post> = listOf()
    val userInfo: MutableMap<String, User> = hashMapOf()

    public fun swapData(data: List<Post>, users: List<User>) {
        this.data = data
        for(u in users) {
            userInfo.put(u.name, u)
        }
        notifyDataSetChanged()
    }

    public fun onUserInfoUpdated(user: User) {
        // see if data has posts by this user => notify update
        val positions = data
                .mapIndexed { (i, post) -> if(post.author == user.name) i else -1 }
                .filter { it >= 0 }
        // update info to be used when rebinding view
        if(!positions.isEmpty()) {
            userInfo.put(user.name, user)
        }
        Timber.d("updating positions $positions")
        for(pos in positions) {
            notifyItemChanged(pos)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        val view = inflater.inflate(R.layout.post_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = data.get(position)
        val userInfo = userInfo.get(post.author)
        if(userInfo != null) {
            Picasso.with(holder.avatar.getContext())
                    .load(Uri.parse(userInfo.avatarUrl))
                    .into(holder.avatar)
        } else {
            // FIXME set some default bg, otherwise it leaves previously used one for a newly binded user
        }
        holder.content.setText(post.content)
        holder.author.setText(post.author)
        holder.timestamp.setText(post.dateString)
        holder.rating.setText(post.rating ?: "")
        holder.rating.setVisibility(if(post.rating != null) View.VISIBLE else View.GONE)
        holder.comments.setText(post.commentCount ?: "")
    }

    override fun getItemCount(): Int {
        return data.size()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content = itemView.findViewById(R.id.content) as TextView
        val author = itemView.findViewById(R.id.author_name) as TextView
        val timestamp = itemView.findViewById(R.id.timestamp) as TextView
        val comments = itemView.findViewById(R.id.comments) as TextView
        val rating = itemView.findViewById(R.id.rating) as TextView
        val avatar = itemView.findViewById(R.id.avatar) as ImageView
    }

}