package com.zy.ppmusic.presenter;

import android.content.Context;

import com.zy.ppmusic.contract.IMediaActivityContract;
import com.zy.ppmusic.model.MediaModelImpl;
import com.zy.ppmusic.utils.DataTransform;
import com.zy.ppmusic.utils.ScanMusicFile;

import java.lang.ref.WeakReference;
import java.util.ArrayList;

public class MediaPresenterImpl implements IMediaActivityContract.IPresenter {
    private WeakReference<IMediaActivityContract.IView> mViewWeak;
    private IMediaActivityContract.IModel mModel;

    public MediaPresenterImpl(IMediaActivityContract.IView mView) {
        this.mViewWeak = new WeakReference<>(mView);
        mModel = new MediaModelImpl();
    }

    @Override
    public void refreshQueue(final Context context) {
        mViewWeak.get().showLoading();
       mModel.refreshQueue(context,new ScanMusicFile.OnScanComplete() {
           @Override
           protected void onComplete(ArrayList<String> paths) {
               DataTransform.getInstance().transFormData(context,paths);
               if (mViewWeak.get() != null) {
                   mViewWeak.get().refreshQueue(paths);
                   mViewWeak.get().hideLoading();
               }
           }
       });
    }

    @Override
    public void destroyView() {
        mViewWeak.clear();
        mViewWeak = null;
        mModel = null;
    }
}