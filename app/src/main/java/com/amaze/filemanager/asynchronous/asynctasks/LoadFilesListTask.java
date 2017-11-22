/*
 * Copyright (C) 2014 Arpit Khurana <arpitkh96@gmail.com>, Vishal Nehra <vishalmeham2@gmail.com>
 *     Emmanuel Messulam <emmanuelbendavid@gmail.com>
 *
 * This file is part of Amaze File Manager.
 *
 * Amaze File Manager is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.amaze.filemanager.asynchronous.asynctasks;

import android.content.Context;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.provider.MediaStore;
import android.support.v4.util.Pair;
import android.text.format.Formatter;

import com.amaze.filemanager.R;
import com.amaze.filemanager.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.asynchronous.runnables.LoadListRunnable;
import com.amaze.filemanager.database.UtilsHandler;
import com.amaze.filemanager.exceptions.CloudPluginException;
import com.amaze.filemanager.exceptions.RootNotPermittedException;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.RootHelper;
import com.amaze.filemanager.fragments.CloudSheetFragment;
import com.amaze.filemanager.fragments.MainFragment;
import com.amaze.filemanager.ui.LayoutElementParcelable;
import com.amaze.filemanager.ui.icons.Icons;
import com.amaze.filemanager.utils.DataUtils;
import com.amaze.filemanager.utils.OTGUtil;
import com.amaze.filemanager.utils.OnAsyncTaskFinished;
import com.amaze.filemanager.utils.OnFileFound;
import com.amaze.filemanager.utils.OpenMode;
import com.amaze.filemanager.utils.application.AppConfig;
import com.amaze.filemanager.utils.cloud.CloudUtil;
import com.amaze.filemanager.utils.files.CryptUtil;
import com.amaze.filemanager.utils.files.FileListSorter;
import com.cloudrail.si.interfaces.CloudStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

public class LoadFilesListTask extends AsyncTask<Void, Void, Pair<OpenMode, ArrayList<LayoutElementParcelable>>> {

    private String path;
    private MainFragment ma;
    private Context c;
    private OpenMode openmode;
    private OnAsyncTaskFinished<Pair<OpenMode, ArrayList<LayoutElementParcelable>>> listener;

    public LoadFilesListTask(Context c, String path, MainFragment ma, OpenMode openmode,
                             OnAsyncTaskFinished<Pair<OpenMode, ArrayList<LayoutElementParcelable>>> l) {
        this.path = path;
        this.ma = ma;
        this.openmode = openmode;
        this.c = c;
        this.listener = l;
    }

    @Override
    protected Pair<OpenMode, ArrayList<LayoutElementParcelable>> doInBackground(Void... p) {
        HybridFile hFile = null;

        if (openmode == OpenMode.UNKNOWN) {
            hFile = new HybridFile(OpenMode.UNKNOWN, path);
            hFile.generateMode(ma.getActivity());
            openmode = hFile.getMode();

            if (hFile.isSmb()) {
                ma.smbPath = path;
            } else if (android.util.Patterns.EMAIL_ADDRESS.matcher(path).matches()) {
                openmode = OpenMode.ROOT;
            }
        }

        if(isCancelled()) return null;

        ma.folder_count = 0;
        ma.file_count = 0;
        final ArrayList<LayoutElementParcelable> list;

        LoadListRunnable loadListRunnable = new LoadListRunnable(ma, openmode, hFile, path);
        list = loadListRunnable.getElementsList();

        if (list != null && !(openmode == OpenMode.CUSTOM && ((path).equals("5") || (path).equals("6")))) {
            Collections.sort(list, new FileListSorter(ma.dsort, ma.sortby, ma.asc));
        }

        return new Pair<>(openmode, list);
    }

    @Override
    protected void onPostExecute(Pair<OpenMode, ArrayList<LayoutElementParcelable>> list) {
        super.onPostExecute(list);
        listener.onAsyncTaskFinished(list);
    }
}
