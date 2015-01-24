import android.support.v7.widget.RecyclerView
import android.view.View
import PostsAdapter.ViewHolder
import com.advaitaworld.app.Post
import android.view.ViewGroup

public class PostsAdapter() : RecyclerView.Adapter<ViewHolder>() {
    var data: List<Post>? = null

    public fun swapData(data: List<Post>) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        throw UnsupportedOperationException()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        throw UnsupportedOperationException()
    }

    override fun getItemCount(): Int {
        throw UnsupportedOperationException()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

    }
}
