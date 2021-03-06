package advaitaworld.net

import com.squareup.okhttp.OkHttpClient
import rx.Observable
import com.squareup.okhttp.ResponseBody
import advaitaworld.util.runOnce
import timber.log.Timber
import com.squareup.okhttp.Request
import java.io.IOException

public fun runRequest(client: OkHttpClient, url: String) : Observable<ResponseBody> {
    return runRequest(client, Request.Builder().url(url).build())
}

public fun runRequest(client: OkHttpClient, request: Request) : Observable<ResponseBody> {
    return runOnce {
        Timber.d("starting request for url ${request.urlString()}")
        val response = client.newCall(request).execute()
        if (!response.isSuccessful()) {
            throw IOException("unexpected http code: ${response.code()}")
        }
        Timber.d("got successful response for ${request.urlString()}")
        response.body()
    }
}
