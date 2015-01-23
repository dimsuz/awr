package com.advaitaworld.app

import rx.Observable

public enum class Section {
    Popular
    Community
    Personal
}

public data class Post(content: CharSequence)

public class Server {
    public fun getPosts(section: Section) : Observable<Post> {
        return Observable.just(
                Post("<h1>hi1</h1>"),
                Post("<h1>hi2</h1>"),
                Post("<h1>hi3</h1>"))
    }
}