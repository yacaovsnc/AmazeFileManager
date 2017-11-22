package com.amaze.filemanager.asynchronous.runnables;

import android.support.annotation.NonNull;
import android.text.Layout;

import com.amaze.filemanager.adapters.RecyclerAdapter;
import com.amaze.filemanager.asynchronous.asynctasks.LoadFilesListTask;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.fragments.MainFragment;
import com.amaze.filemanager.ui.LayoutElementParcelable;
import com.amaze.filemanager.utils.OpenMode;

import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

/**
 * Created by Vishal Nehra on 11/21/2017.
 *
 * Class helps with synchronizing an existing copy of list elements with a new copy
 * which loads in the background and post results as per the changes, while an existing copy
 * is still visible to the user. Use is to create a consistent and smooth UX.
 */

public class SynchronizeFilesList {

    private ArrayList<LayoutElementParcelable> existingLayoutElementParcelables, newLayoutElementParcelables;
    private String path;
    private FutureTask futureTask;
    private MainFragment mainFragment;
    private OpenMode openMode;
    private HybridFile hybridFile;

    public SynchronizeFilesList(MainFragment mainFragment, ArrayList<LayoutElementParcelable> layoutElementParcelables, String path,
                                OpenMode openMode) {

        this.existingLayoutElementParcelables = layoutElementParcelables;
        this.path = path;
        this.openMode = openMode;
        this.mainFragment = mainFragment;
    }

    public void watch(SynchronizeResultsCallback synchronizeResultsCallback) {


        CustomExecutor customExecutor = new CustomExecutor();

        futureTask = new FutureTask(() -> {

            try {

                mainFragment.folder_count = 0;
                mainFragment.file_count = 0;

                if (openMode == OpenMode.UNKNOWN) {
                    hybridFile = new HybridFile(OpenMode.UNKNOWN, path);
                    hybridFile.generateMode(mainFragment.getActivity());
                    openMode = hybridFile.getMode();

                    if (hybridFile.isSmb()) {
                        mainFragment.smbPath = path;
                    } else if (android.util.Patterns.EMAIL_ADDRESS.matcher(path).matches()) {
                        openMode = OpenMode.ROOT;
                    }
                }

                LoadListRunnable loadListRunnable = new LoadListRunnable(mainFragment, openMode, hybridFile, path);
                newLayoutElementParcelables = loadListRunnable.getElementsList();
            } finally {
                for (LayoutElementParcelable existingListElement : existingLayoutElementParcelables) {

                    LayoutElementParcelable layoutElementParcelable = null;
                    for (LayoutElementParcelable newListElement : newLayoutElementParcelables) {
                        // iterate through every new list element to find matching from existing list
                        // and make changes in adapter according to new data
                        if (existingListElement.equals(newListElement)) {
                            layoutElementParcelable = newListElement;
                        }
                    }

                    if (layoutElementParcelable == null) {
                        // element not found in the list, add to the adapter
                        synchronizeResultsCallback.newElement(layoutElementParcelable);
                    }
                }
            }
            return null;
        });

        customExecutor.execute(futureTask);
    }

    /**
     * Determines whether processing of this task is still in progress.
     * @return
     */
    public boolean isCancelled() {

        return futureTask.isCancelled();
    }

    /**
     * Cancels the task
     */
    public void stopWatch() {
        futureTask.cancel(true);
    }

    public interface SynchronizeResultsCallback {
        boolean isSuccessful();
        boolean timeout();
        void newElement(LayoutElementParcelable layoutElementParcelable);
        int removeElement();
    }

    private class CustomExecutor implements Executor {

        @Override
        public void execute(@NonNull Runnable command) {
            Thread thread = new Thread(command);
            thread.start();
        }
    }
}
