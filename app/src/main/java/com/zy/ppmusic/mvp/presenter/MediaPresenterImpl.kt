package com.zy.ppmusic.mvp.presenter

import android.content.Context
import android.os.Bundle
import android.os.ResultReceiver
import android.support.v4.media.session.MediaControllerCompat
import com.zy.ppmusic.mvp.contract.IMediaActivityContract
import com.zy.ppmusic.mvp.model.MediaActivityModelImpl
import com.zy.ppmusic.utils.DataProvider
import com.zy.ppmusic.utils.FileUtils
import com.zy.ppmusic.utils.PrintLog
import com.zy.ppmusic.utils.TaskPool

/**
 * @author ZhiTouPC
 */
class MediaPresenterImpl(view: IMediaActivityContract.IMediaActivityView) :
        IMediaActivityContract.AbstractMediaActivityPresenter(view) {
    override fun getChildrenUri(): String {
        return mModel.getGrantedChildrenUri()
    }

    override fun removeQueueItem(position: Int) {
        mModel?.removeQueueItem(position)
    }

    override fun getGrantedRootUri(): String {
        return mModel.getGrantedRootUri()
    }

    override fun setGrantedRootUri(uri: String, child: String) {
        mModel.saveGrantedUri(uri, child)
    }

    override fun createModel(): IMediaActivityContract.IMediaActivityModel {
        return MediaActivityModelImpl()
    }

    override fun attachModelController(controller: MediaControllerCompat?) {
        mModel.attachController(controller)
    }

    override fun refreshQueue(isRefresh: Boolean) {
        if (mView.get() == null) {
            PrintLog.i("view is null")
            return
        }
        mView.get()?.showLoading()
        TaskPool.executeSyc(Runnable {
            DataProvider.get().loadData(isRefresh,object:DataProvider.OnLoadCompleteListener{
                override fun onLoadComplete() {
                    mView.get()?.loadFinished()
                    mView.get()?.hideLoading()
                }
            })
        })
    }


    override fun getLocalMode(c: Context) {
        mModel.getLocalMode(c, e = {
            mView?.get()?.setRepeatMode(it)
        })
    }

    override fun deleteFile(path: String?): Boolean {
        return FileUtils.deleteFile(path)
    }

    override fun sendCommand(method: String, params: Bundle, resultReceiver: ResultReceiver) {
        mModel.postSendCommand(method, params, resultReceiver)
    }

    override fun playWithId(mediaId: String, extra: Bundle) {
        println("presenter .... playWithId")
        mModel.postPlayWithId(mediaId, extra)
    }

    override fun sendCustomAction(action: String, extra: Bundle) {
        mModel.postSendCustomAction(action, extra)
    }

    override fun setRepeatMode(context: Context, mode: Int) {
        mModel.postSetRepeatMode(context, mode)
    }

    override fun skipNext() {
        println("presenter .... skipNext")
        mModel.postSkipNext()
    }

    override fun skipToPosition(id: Long) {
        mModel.postSkipToPosition(id)
    }

    override fun skipPrevious() {
        println("presenter .... postSkipPrevious")
        mModel.postSkipPrevious()
    }

    override fun detachViewAndModel() {
        super.detachViewAndModel()
        mModel.shutdown()
    }

    companion object {
        private const val TAG = "MediaPresenterImpl"
    }

}
