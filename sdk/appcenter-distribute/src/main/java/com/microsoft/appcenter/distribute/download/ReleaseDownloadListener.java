/*
 * Copyright (c) Microsoft Corporation. All rights reserved.
 * Licensed under the MIT License.
 */

package com.microsoft.appcenter.distribute.download;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.support.annotation.NonNull;

import com.microsoft.appcenter.distribute.Distribute;
import com.microsoft.appcenter.distribute.R;
import com.microsoft.appcenter.distribute.ReleaseDetails;
import com.microsoft.appcenter.utils.AppCenterLog;
import com.microsoft.appcenter.utils.HandlerUtils;
import com.microsoft.appcenter.utils.storage.SharedPreferencesManager;

import java.text.NumberFormat;

import static com.microsoft.appcenter.distribute.DistributeConstants.HANDLER_TOKEN_CHECK_PROGRESS;
import static com.microsoft.appcenter.distribute.DistributeConstants.LOG_TAG;
import static com.microsoft.appcenter.distribute.DistributeConstants.MEBIBYTE_IN_BYTES;
import static com.microsoft.appcenter.distribute.download.DownloadUtils.PREFERENCE_KEY_DOWNLOADED_DISTRIBUTION_GROUP_ID;
import static com.microsoft.appcenter.distribute.download.DownloadUtils.PREFERENCE_KEY_DOWNLOADED_RELEASE_HASH;
import static com.microsoft.appcenter.distribute.download.DownloadUtils.PREFERENCE_KEY_DOWNLOADED_RELEASE_ID;

public class ReleaseDownloadListener implements ReleaseDownloader.Listener {

    @SuppressLint("StaticFieldLeak")
    private static Context mContext;

    /**
     * Last download progress dialog that was shown.
     * Android 8 deprecates this dialog but only reason is that they want us to use a non modal
     * progress indicator while we actually use it to be a modal dialog for forced update.
     * They will always keep this dialog to remain compatible but just mark it deprecated.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private android.app.ProgressDialog mProgressDialog;

    public ReleaseDownloadListener(@NonNull Context context) {
        mContext = context;
    }

    private static Uri getFileUriOnOldDevices(String localUrl) {
        return Uri.parse("file://" + localUrl);
    }

    static void storeReleaseDetails(@NonNull ReleaseDetails releaseDetails) {
        String groupId = releaseDetails.getDistributionGroupId();
        String releaseHash = releaseDetails.getReleaseHash();
        int releaseId = releaseDetails.getId();
        AppCenterLog.debug(LOG_TAG, "Stored release details: group id=" + groupId + " release hash=" + releaseHash + " release id=" + releaseId);
        SharedPreferencesManager.putString(PREFERENCE_KEY_DOWNLOADED_DISTRIBUTION_GROUP_ID, groupId);
        SharedPreferencesManager.putString(PREFERENCE_KEY_DOWNLOADED_RELEASE_HASH, releaseHash);
        SharedPreferencesManager.putInt(PREFERENCE_KEY_DOWNLOADED_RELEASE_ID, releaseId);
    }

    /**
     * Get the intent used to open installation U.I.
     *
     * @param fileUri downloaded file URI from the download manager.
     * @return intent to open installation U.I.
     */
    @NonNull
    static Intent getInstallIntent(Uri fileUri) {
        Intent intent = new Intent(Intent.ACTION_INSTALL_PACKAGE);
        intent.setData(fileUri);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return intent;
    }

    @Override
    public void onProgress(@NonNull DownloadProgress downloadProgress) {
        AppCenterLog.verbose(LOG_TAG, "downloadedBytes=" + downloadProgress.getCurrentSize() + " totalBytes=" + downloadProgress.getTotalSize());

        /* If file size is known update downloadProgress bar. */
        if (mProgressDialog != null && downloadProgress.getTotalSize() >= 0) {

            /* When we switch from indeterminate to determinate */
            if (mProgressDialog.isIndeterminate()) {

                /* Configure the progress dialog determinate style. */
                mProgressDialog.setProgressPercentFormat(NumberFormat.getPercentInstance());
                mProgressDialog.setProgressNumberFormat(mContext.getString(R.string.appcenter_distribute_download_progress_number_format));
                mProgressDialog.setIndeterminate(false);
                mProgressDialog.setMax((int) (downloadProgress.getTotalSize() / MEBIBYTE_IN_BYTES));
            }
            mProgressDialog.setProgress((int) (downloadProgress.getCurrentSize() / MEBIBYTE_IN_BYTES));
        }
    }

    @Override
    public void onComplete(@NonNull String localUri, @NonNull ReleaseDetails releaseDetails) {
        AppCenterLog.debug(LOG_TAG, "Download was successful uri=" + localUri);
        Intent intent = getInstallIntent(Uri.parse(localUri));
        boolean installerFound = intent.resolveActivity(mContext.getPackageManager()) != null;
//      if (!installerFound) {
//          if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
//              intent = DownloadUtils.getInstallIntent(getFileUriOnOldDevices(cursor));
//              installerFound = intent.resolveActivity(mContext.getPackageManager()) != null;
//          }
//      } else {
//          installerFound = true;
//      }
        if (!installerFound) {
            AppCenterLog.error(LOG_TAG, "Installer not found");
//            distribute.completeWorkflow(mReleaseDetails);
//            return null;
        }

        // TODO Check if a should install now.

        AppCenterLog.info(LOG_TAG, "Show install UI now intentUri=" + intent.getData());
        mContext.startActivity(intent);
        if (releaseDetails.isMandatoryUpdate()) {
            Distribute.getInstance().setInstalling(releaseDetails);
        }
        storeReleaseDetails(releaseDetails);
    }

    @Override
    public void onError(@NonNull String errorMessage) {

    }


    /**
     * Show download progress.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    public void showDownloadProgress() {
        if (mForegroundActivity == null) {
            AppCenterLog.warn(LOG_TAG, "Could not display progress dialog in the background.");
            return;
        }
        mProgressDialog = new android.app.ProgressDialog(mForegroundActivity);
        mProgressDialog.setTitle(R.string.appcenter_distribute_downloading_mandatory_update);
        mProgressDialog.setCancelable(false);
        mProgressDialog.setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL);
        mProgressDialog.setIndeterminate(true);
        mProgressDialog.setProgressNumberFormat(null);
        mProgressDialog.setProgressPercentFormat(null);
        showAndRememberDialogActivity(mProgressDialog);
    }

    /**
     * Hide progress dialog and stop updating.
     */
    @SuppressWarnings({"deprecation", "RedundantSuppression"})
    private synchronized void hideProgressDialog() {
        if (mProgressDialog != null) {
            final android.app.ProgressDialog progressDialog = mProgressDialog;
            mProgressDialog = null;

            /* This can be called from background check download task. */
            HandlerUtils.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    progressDialog.hide();
                }
            });
            HandlerUtils.getMainHandler().removeCallbacksAndMessages(HANDLER_TOKEN_CHECK_PROGRESS);
        }
    }
}
