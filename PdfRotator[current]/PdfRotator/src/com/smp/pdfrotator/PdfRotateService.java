package com.smp.pdfrotator;

import java.util.List;
import android.app.IntentService;
import android.content.Intent;
import android.widget.Toast;
import group.pals.android.lib.ui.filechooser.io.localfile.LocalFile;
import static com.smp.pdfrotator.Constants.*;

public class PdfRotateService extends IntentService
{
	static Boolean trial = true;
	int badFiles;
	
	public PdfRotateService()
	{
		super("PdfRotateService");
	}

	@Override
	public void onCreate()
	{
		super.onCreate();
		trial = PdfRotateService.this.getPackageName().equals(FREE_VERSION);
	}
	@Override
	protected void onHandleIntent(Intent intent)
	{
		List<LocalFile> pdfs = intent.getParcelableArrayListExtra(PDF_EXTRA);

		int angle = intent.getIntExtra(ANGLE_EXTRA, 0);

		badFiles = PdfHandler.rotatePdfs(pdfs, angle);

		Intent resultIntent = new Intent();
		resultIntent.putExtra(BAD_FILES, badFiles)
				.setAction(ACTION_RESP)
				.addCategory(Intent.CATEGORY_DEFAULT);

		sendBroadcast(resultIntent);
	}

	@Override
	public void onDestroy()
	{
		super.onDestroy();
		makeDoneToast(badFiles);
	}

	private void makeDoneToast(int badFiles)
	{
		CharSequence text = badFiles == 0 ? "All Pdf's rotated successfully." :
				"" + badFiles + " Pdf's could not be rotated.";
		int duration = Toast.LENGTH_LONG;
		Toast.makeText(this, text, duration).show();
	}

}
