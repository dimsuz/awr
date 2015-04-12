package advaitaworld

import android.support.v7.widget.RecyclerView
import android.view.ViewGroup
import android.view.View
import android.widget.TextView
import android.widget.ImageView
import android.view.LayoutInflater
import android.net.Uri
import com.squareup.picasso.Picasso
import advaitaworld.PostFeedAdapter.ViewHolder
import timber.log.Timber
import advaitaworld.parsing.ShortPostInfo
import advaitaworld.parsing.User
import android.content.Intent
import rx.android.lifecycle.LifecycleObservable
import rx.android.lifecycle.LifecycleEvent
import rx.schedulers.Schedulers
import rx.android.schedulers.AndroidSchedulers
import rx.Observable
import rx.Subscription

public class PostFeedAdapter(val lifecycle: Observable<LifecycleEvent>) : RecyclerView.Adapter<ViewHolder>() {
    private var data: List<ShortPostInfo> = listOf()
    private val userInfoMap: MutableMap<String, User> = hashMapOf()
    private var userDataSubscription: Subscription? = null

    public fun swapData(data: List<ShortPostInfo>) {
        this.data = data
        notifyDataSetChanged()
        startFetchingUserInfo()
    }

    private fun startFetchingUserInfo() {
        if(userDataSubscription != null) {
            userDataSubscription!!.unsubscribe()
        }
        val userData = UserInfoProvider.getUsersByName(data.map { it.content.author })
        userDataSubscription = LifecycleObservable.bindUntilLifecycleEvent(lifecycle, userData, LifecycleEvent.DESTROY)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        { onUserInfoUpdated(it) },
                        { exception -> Timber.e(exception, "failed to fetch user info") },
                        { Timber.i("user info fetched, info map contains ${userInfoMap.size()} items") })
    }

    public fun onUserInfoUpdated(user: User) {
        // see if data has posts by this user => notify update
        val positions = data
                .mapIndexed { i, post -> if(post.content.author == user.name) i else -1 }
                .filter { it >= 0 }
        // update info to be used when rebinding view
        if(!positions.isEmpty()) {
            userInfoMap.put(user.name, user)
        }
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
        val userInfo = userInfoMap.get(post.content.author)
        if(userInfo != null) {
            Picasso.with(holder.avatar.getContext())
                    .load(Uri.parse(userInfo.avatarUrl))
                    .placeholder(R.drawable.placeholder_avatar)
                    .error(if(BuildConfig.DEBUG) R.drawable.placeholder_error else R.drawable.placeholder_avatar)
                    .into(holder.avatar)
        } else {
            holder.avatar.setImageResource(R.drawable.placeholder_avatar)
        }
        holder.title.setText(post.title)
        holder.content.setText(post.content.text)
        holder.author.setText(post.content.author)
        holder.timestamp.setText(post.content.dateString)
        holder.rating.setText(post.content.rating ?: "")
        holder.rating.setVisibility(if(post.content.rating != null) View.VISIBLE else View.GONE)
        holder.comments.setText(post.commentCount ?: "")
    }

    override fun getItemCount(): Int {
        return data.size()
    }

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val title = itemView.findViewById(R.id.title) as TextView
        val content = itemView.findViewById(R.id.content) as TextView
        val author = itemView.findViewById(R.id.author_name) as TextView
        val timestamp = itemView.findViewById(R.id.timestamp) as TextView
        val comments = itemView.findViewById(R.id.comments) as TextView
        val rating = itemView.findViewById(R.id.rating) as TextView
        val avatar = itemView.findViewById(R.id.avatar) as ImageView

        init {
            comments.setOnClickListener {
                val context = comments.getContext()
                val intent = Intent(context, javaClass<PostActivity>())
                intent.putExtra(EXTRA_POST_ID, data.get(getPosition()).postId)
                context.startActivity(intent)
            }
        }
    }

}