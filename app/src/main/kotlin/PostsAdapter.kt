package com.advaitaworld.app

import android.support.v7.widget.RecyclerView
import android.view.View
import android.view.ViewGroup
import android.view.LayoutInflater
import com.advaitaworld.app.PostsAdapter.ViewHolder
import android.widget.TextView

public class PostsAdapter() : RecyclerView.Adapter<ViewHolder>() {
    var data: List<Post> = listOf()

    public fun swapData(data: List<Post>) {
        this.data = data
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val inflater = LayoutInflater.from(parent.getContext())
        val view = inflater.inflate(R.layout.post_item, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.content.setText(data.get(position).content)
    }

    override fun getItemCount(): Int {
        return data.size()
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val content = itemView.findViewById(R.id.content) as TextView
    }
}
