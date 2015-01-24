package com.advaitaworld.app

import android.support.v7.app.ActionBarActivity
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.support.v7.widget.RecyclerView
import rx.android.app.AppObservable
import rx.schedulers.Schedulers
import android.support.v7.widget.LinearLayoutManager
import com.advaitaworld.app.util.decorations.SpaceItemDecoration


public class MainActivity : ActionBarActivity() {
    var adapter: PostsAdapter? = null
    val server = Server()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setupPostsList()
        fetchPosts()
    }

    private fun setupPostsList() {
        val listView = findViewById(R.id.post_list) as RecyclerView
        listView.setLayoutManager(LinearLayoutManager(this, LinearLayoutManager.VERTICAL, false))
        listView.setHasFixedSize(true)
        listView.addItemDecoration(SpaceItemDecoration(
                getResources().getDimensionPixelSize(R.dimen.card_vertical_margin)))
        adapter = PostsAdapter()
        listView.setAdapter(adapter)
    }

    private fun fetchPosts() {
        AppObservable.bindActivity(this, server.getPosts(Section.Popular))
                .subscribeOn(Schedulers.io())
                .subscribe({ list ->
                    adapter?.swapData(list)
                }, { /* error */ })
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        val id = item.getItemId()

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true
        }

        return super.onOptionsItemSelected(item)
    }
}
