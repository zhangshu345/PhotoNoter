package com.yydcdut.note.service;

import android.app.IntentService;
import android.content.Intent;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.yydcdut.note.NoteApplication;
import com.yydcdut.note.bean.Category;
import com.yydcdut.note.bean.PhotoNote;
import com.yydcdut.note.model.CategoryDBModel;
import com.yydcdut.note.model.PhotoNoteDBModel;
import com.yydcdut.note.mvp.v.service.ICheckServiceView;
import com.yydcdut.note.utils.Const;
import com.yydcdut.note.utils.FilePathUtils;

import java.util.List;

/**
 * Created by yuyidong on 15/7/17.
 */
public class CheckService extends IntentService implements ICheckServiceView {

    private CategoryDBModel mCategoryDBModel;
    private PhotoNoteDBModel mPhotoNoteDBModel;

    public CheckService() {
        super("com.yydcdut.note.service.CheckService");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        mCategoryDBModel = ((NoteApplication) getApplication()).getApplicationComponent().getCategoryDBModel();
        mPhotoNoteDBModel = ((NoteApplication) getApplication()).getApplicationComponent().getPhotoNoteDBModel();
        checkCategoryPhotoNumber();
        checkBigAndSmallPhoto();
    }

    /**
     * 判断category中的photoNumber是否正确
     */
    private void checkCategoryPhotoNumber() {
        List<Category> categoryList = mCategoryDBModel.findAll();
        boolean isChanged = false;
        for (Category category : categoryList) {
            List<PhotoNote> photoNoteList = mPhotoNoteDBModel.findByCategoryLabel(category.getLabel(), -1);
            if (category.getPhotosNumber() != photoNoteList.size()) {
                category.setPhotosNumber(photoNoteList.size());
                isChanged = true;
            }
        }
        if (isChanged) {
            mCategoryDBModel.updateCategoryListInService(categoryList);
        }
    }

    /**
     * 判断大图片和小图片是否同时存在
     */
    private void checkBigAndSmallPhoto() {
        //大图不在&小图在&数据库在，说明可能是人为删除的，所以同时把小图和数据库中的数据删除
        //小图不在&大图在&数据库在，说明可能是系统删除的，所以生成一张小图
        //数据库不在&大图小图都在，删除大图小图
        List<Category> categoryList = mCategoryDBModel.findAll();
        for (Category category : categoryList) {
            List<PhotoNote> photoNoteList = mPhotoNoteDBModel.findByCategoryLabel(category.getLabel(), -1);
            for (int i = 0; i < photoNoteList.size(); i++) {
                PhotoNote photoNote = photoNoteList.get(i);
                int result = FilePathUtils.isFileExist(photoNote.getPhotoName());
                switch (result) {
                    case FilePathUtils.ALL_NOT_EXIST:
                    case FilePathUtils.BIG_PHOTO_NOT_EXIST:
                        // java.util.ConcurrentModificationException
                        mPhotoNoteDBModel.delete(photoNote);
                        FilePathUtils.deleteAllFiles(photoNote.getPhotoName());
                        break;
                    case FilePathUtils.SMALL_PHOTO_NOT_EXIST:
                        FilePathUtils.saveSmallPhotoFromBigPhoto(photoNote);
                        break;
                    case FilePathUtils.ALL_EXIST:
                    default:
                        break;
                }
            }
        }
    }

    @Override
    public void onDestroy() {
        Intent intent = new Intent();
        intent.setAction(Const.BROADCAST_PHOTONOTE_UPDATE);
        intent.putExtra(Const.TARGET_BROADCAST_SERVICE, true);
        sendBroadcast(intent);
        super.onDestroy();

    }

    @Override
    public void stopService() {

    }
}
