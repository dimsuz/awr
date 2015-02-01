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

public class PostsAdapter() : RecyclerView.Adapter<ViewHolder>() {
    var data: List<Post> = listOf()

    public fun swapData(data: List<Post>) {
        this.data = data
        notifyDataSetChanged()
    }

    public fun onUserInfoUpdated(user: User) {
        // TODO see if data has posts by this user => notify update
        throw UnsupportedOperationException("not implemented")
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        val view = inflater.inflate(R.layout.post_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val post = data.get(position)
        Picasso.with(holder.avatar.getContext())
                .load(Uri.parse("http://advaitaworld.com/uploads/images/00/42/29/2014/10/09/avatar_100x100.jpg?080703"))
                .into(holder.avatar)
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