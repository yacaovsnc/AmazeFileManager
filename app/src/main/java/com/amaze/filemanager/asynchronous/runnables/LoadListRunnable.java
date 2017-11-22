package com.amaze.filemanager.asynchronous.runnables;

import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.provider.MediaStore;
import android.text.format.Formatter;

import com.amaze.filemanager.R;
import com.amaze.filemanager.activities.superclasses.ThemedActivity;
import com.amaze.filemanager.database.UtilsHandler;
import com.amaze.filemanager.exceptions.CloudPluginException;
import com.amaze.filemanager.filesystem.HybridFile;
import com.amaze.filemanager.filesystem.HybridFileParcelable;
import com.amaze.filemanager.filesystem.RootHelper;
import com.amaze.filemanager.fragments.CloudSheetFragment;
import com.amaze.filemanager.fragments.MainFragment;
import com.amaze.filemanager.ui.LayoutElementParcelable;
import com.amaze.filemanager.ui.icons.Icons;
import com.amaze.filemanager.utils.DataUtils;
import com.amaze.filemanager.utils.OTGUtil;
import com.amaze.filemanager.utils.OnFileFound;
import com.amaze.filemanager.utils.OpenMode;
import com.amaze.filemanager.utils.application.AppConfig;
import com.amaze.filemanager.utils.cloud.CloudUtil;
import com.amaze.filemanager.utils.files.CryptUtil;
import com.cloudrail.si.interfaces.CloudStorage;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;

import jcifs.smb.SmbAuthException;
import jcifs.smb.SmbException;
import jcifs.smb.SmbFile;

/**
 * Created by Vishal Nehra on 11/21/2017.
 * A runnable class used to load list and can be re-used anywhere without depending on
 * {@link com.amaze.filemanager.asynchronous.asynctasks.LoadFilesListTask}
 */

public class LoadListRunnable implements Runnable {

    private ArrayList<LayoutElementParcelable> elementParcelables;
    private MainFragment mainFragment;
    private OpenMode openMode;
    private HybridFile hybridFile;
    private String path;
    private Drawable lockBitmapDrawable = null;
    private DataUtils dataUtils = DataUtils.getInstance();

    public LoadListRunnable(MainFragment mainFragment, OpenMode openMode, HybridFile hybridFile, String path) {

        this.mainFragment = mainFragment;
        this.openMode = openMode;
        this.hybridFile = hybridFile;
        this.path = path;
    }

    /**
     * Returns the list after processing. Be advised, this will be run on main thread, so it is
     * strictly recommended you initialize an object to this runnable from a background thread.
     * @return
     */
    public ArrayList<LayoutElementParcelable> getElementsList() {
        try {
            this.run();
        } finally {
            if (elementParcelables == null) {
                return new ArrayList<>();
            } else {

                return elementParcelables;
            }
        }
    }

    @Override
    public void run() {

        switch (openMode) {
            case SMB:
                if (hybridFile == null) {
                    hybridFile = new HybridFile(OpenMode.SMB, path);
                }

                try {
                    SmbFile[] smbFile = hybridFile.getSmbFile(5000).listFiles();
                    elementParcelables = mainFragment.addToSmb(smbFile, path);
                    openMode = OpenMode.SMB;
                } catch (SmbAuthException e) {
                    if (!e.getMessage().toLowerCase().contains("denied")) {
                        mainFragment.reauthenticateSmb();
                    }
                    elementParcelables = null;
                } catch (SmbException | NullPointerException e) {
                    e.printStackTrace();
                    elementParcelables = null;
                }
                break;
            case CUSTOM:
                switch (Integer.parseInt(path)) {
                    case 0:
                        elementParcelables = listImages();
                        break;
                    case 1:
                        elementParcelables = listVideos();
                        break;
                    case 2:
                        elementParcelables = listaudio();
                        break;
                    case 3:
                        elementParcelables = listDocs();
                        break;
                    case 4:
                        elementParcelables = listApks();
                        break;
                    case 5:
                        elementParcelables = listRecent();
                        break;
                    case 6:
                        elementParcelables = listRecentFiles();
                        break;
                    default:
                        elementParcelables = null;
                        throw new IllegalStateException();
                }

                break;
            case OTG:
                elementParcelables = new ArrayList<>();
                listOtg(path, file -> {
                    LayoutElementParcelable elem = createListParcelables(file);
                    if (elem != null) elementParcelables.add(elem);
                });
                openMode = openMode.OTG;
                break;
            case DROPBOX:
            case BOX:
            case GDRIVE:
            case ONEDRIVE:
                CloudStorage cloudStorage = dataUtils.getAccount(openMode);
                elementParcelables = new ArrayList<>();

                try {
                    listCloud(path, cloudStorage, openMode, file -> {
                        LayoutElementParcelable elem = createListParcelables(file);
                        if (elem != null) elementParcelables.add(elem);
                    });
                } catch (CloudPluginException e) {
                    e.printStackTrace();
                    AppConfig.toast(mainFragment.getContext(),
                            mainFragment.getContext().getResources().getString(R.string.failed_no_connection));
                    elementParcelables = null;
                }
                break;
            default:
                // we're neither in OTG not in SMB, load the list based on root/general filesystem
                elementParcelables = new ArrayList<>();
                RootHelper.getFiles(path, ThemedActivity.rootMode, mainFragment.SHOW_HIDDEN,
                        mode -> openMode = mode, file -> {
                            LayoutElementParcelable elem = createListParcelables(file);
                            if (elem != null) elementParcelables.add(elem);
                        });
                break;
        }
    }

    private LayoutElementParcelable createListParcelables(HybridFileParcelable baseFile) {
        if (!dataUtils.isFileHidden(baseFile.getPath())) {
            String size = "";
            Drawable drawable;
            long longSize= 0;

            if (baseFile.isDirectory()) {
                if(lockBitmapDrawable == null) {
                    lockBitmapDrawable = mainFragment.getResources().getDrawable(R.drawable.ic_folder_lock_white_36dp);
                }

                drawable = baseFile.getName().endsWith(CryptUtil.CRYPT_EXTENSION)? lockBitmapDrawable:mainFragment.folder;
                mainFragment.folder_count++;
            } else {
                if (baseFile.getSize() != -1) {
                    try {
                        longSize = baseFile.getSize();
                        size = Formatter.formatFileSize(mainFragment.getContext(), longSize);
                    } catch (NumberFormatException e) {
                        e.printStackTrace();
                    }
                }
                drawable = Icons.loadMimeIcon(baseFile.getPath(), !mainFragment.IS_LIST, mainFragment.getResources());
                mainFragment.file_count++;
            }

            LayoutElementParcelable layoutElement = new LayoutElementParcelable(drawable,
                    baseFile.getPath(), baseFile.getPermission(), baseFile.getLink(), size,
                    longSize, baseFile.isDirectory(), false, baseFile.getDate() + "");
            layoutElement.setMode(baseFile.getMode());
            return layoutElement;
        }

        return null;
    }

    private ArrayList<LayoutElementParcelable> listImages() {
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        final String[] projection = {MediaStore.Images.Media.DATA};
        final Cursor cursor = mainFragment.getContext().getContentResolver().query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null);
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                if (strings != null) {
                    LayoutElementParcelable parcelable = createListParcelables(strings);
                    if(parcelable != null) songs.add(parcelable);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listVideos() {
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        final String[] projection = {MediaStore.Images.Media.DATA};
        final Cursor cursor = mainFragment.getContext().getContentResolver().query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null);
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                if (strings != null) {
                    LayoutElementParcelable parcelable = createListParcelables(strings);
                    if(parcelable != null) songs.add(parcelable);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listaudio() {
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        String[] projection = {
                MediaStore.Audio.Media.DATA
        };

        Cursor cursor = mainFragment.getContext().getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                null);

        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                if (strings != null) {
                    LayoutElementParcelable parcelable = createListParcelables(strings);
                    if(parcelable != null) songs.add(parcelable);
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listDocs() {
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        final String[] projection = {MediaStore.Files.FileColumns.DATA};
        Cursor cursor = mainFragment.getContext().getContentResolver().query(MediaStore.Files.getContentUri("external"),
                projection, null, null, null);
        String[] types = new String[]{".pdf", ".xml", ".html", ".asm", ".text/x-asm", ".def", ".in", ".rc",
                ".list", ".log", ".pl", ".prop", ".properties", ".rc",
                ".doc", ".docx", ".msg", ".odt", ".pages", ".rtf", ".txt", ".wpd", ".wps"};
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                if (path != null && Arrays.asList(types).contains(path)) {
                    HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                    if (strings != null) {
                        LayoutElementParcelable parcelable = createListParcelables(strings);
                        if(parcelable != null) songs.add(parcelable);
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        Collections.sort(songs, (lhs, rhs) -> -1 * Long.valueOf(lhs.getDate1()).compareTo(rhs.getDate1()));
        if (songs.size() > 20)
            for (int i = songs.size() - 1; i > 20; i--) {
                songs.remove(i);
            }
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listApks() {
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        final String[] projection = {MediaStore.Files.FileColumns.DATA};

        Cursor cursor = mainFragment.getContext().getContentResolver()
                .query(MediaStore.Files.getContentUri("external"), projection, null, null, null);
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                if (path != null && path.endsWith(".apk")) {
                    HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                    if (strings != null) {
                        LayoutElementParcelable parcelable = createListParcelables(strings);
                        if(parcelable != null) songs.add(parcelable);
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listRecent() {
        UtilsHandler utilsHandler = new UtilsHandler(mainFragment.getContext());
        final ArrayList<String> paths = utilsHandler.getHistoryList();
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        for (String f : paths) {
            if (!f.equals("/")) {
                HybridFileParcelable hybridFileParcelable = RootHelper.generateBaseFile(new File(f), mainFragment.SHOW_HIDDEN);
                if (hybridFileParcelable != null) {
                    hybridFileParcelable.generateMode(mainFragment.getActivity());
                    if (!hybridFileParcelable.isSmb() && !hybridFileParcelable.isDirectory() && hybridFileParcelable.exists()) {
                        LayoutElementParcelable parcelable = createListParcelables(hybridFileParcelable);
                        if (parcelable != null) songs.add(parcelable);
                    }
                }
            }
        }
        return songs;
    }

    private ArrayList<LayoutElementParcelable> listRecentFiles() {
        ArrayList<LayoutElementParcelable> songs = new ArrayList<>();
        final String[] projection = {MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DATE_MODIFIED};
        Calendar c = Calendar.getInstance();
        c.set(Calendar.DAY_OF_YEAR, c.get(Calendar.DAY_OF_YEAR) - 2);
        Date d = c.getTime();
        Cursor cursor = this.mainFragment.getContext().getContentResolver().query(MediaStore.Files
                        .getContentUri("external"), projection,
                null,
                null, null);
        if (cursor == null) return songs;
        if (cursor.getCount() > 0 && cursor.moveToFirst()) {
            do {
                String path = cursor.getString(cursor.getColumnIndex
                        (MediaStore.Files.FileColumns.DATA));
                File f = new File(path);
                if (d.compareTo(new Date(f.lastModified())) != 1 && !f.isDirectory()) {
                    HybridFileParcelable strings = RootHelper.generateBaseFile(new File(path), mainFragment.SHOW_HIDDEN);
                    if (strings != null) {
                        LayoutElementParcelable parcelable = createListParcelables(strings);
                        if(parcelable != null) songs.add(parcelable);
                    }
                }
            } while (cursor.moveToNext());
        }
        cursor.close();
        Collections.sort(songs, (lhs, rhs) -> -1 * Long.valueOf(lhs.getDate1()).compareTo(rhs.getDate1()));
        if (songs.size() > 20)
            for (int i = songs.size() - 1; i > 20; i--) {
                songs.remove(i);
            }
        return songs;
    }

    /**
     * Lists files from an OTG device
     *
     * @param path the path to the directory tree, starts with prefix {@link com.amaze.filemanager.utils.OTGUtil#PREFIX_OTG}
     *             Independent of URI (or mount point) for the OTG
     */
    private void listOtg(String path, OnFileFound fileFound) {
        OTGUtil.getDocumentFiles(path, mainFragment.getContext(), fileFound);
    }

    private void listCloud(String path, CloudStorage cloudStorage, OpenMode openMode,
                           OnFileFound fileFoundCallback) throws CloudPluginException {
        if (!CloudSheetFragment.isCloudProviderAvailable(mainFragment.getContext())) {
            throw new CloudPluginException();
        }

        CloudUtil.getCloudFiles(path, cloudStorage, openMode, fileFoundCallback);
    }
}
