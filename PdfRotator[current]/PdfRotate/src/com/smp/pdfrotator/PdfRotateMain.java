package com.smp.pdfrotator;

import java.util.ArrayList;
import java.util.List;

import com.mobeta.android.dslv.DragSortController;
import com.mobeta.android.dslv.DragSortListView;

import group.pals.android.lib.ui.filechooser.FileChooserActivity;
import group.pals.android.lib.ui.filechooser.io.localfile.LocalFile;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcelable;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.app.AlertDialog;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemSelectedListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import static com.smp.pdfrotator.Constants.*;

public class PdfRotateMain extends Activity
{
	private static Boolean trial = true;

	private List<LocalFile> chosenPdfs = new ArrayList<LocalFile>();

	private ProgressBar progress;
	private TextView progressText;
	private Spinner rotationSpinner;
	private DragSortListView listView;
	private ArrayAdapter<String> listAdapter;
	private ImageView image;
	private Button rotateButton;

	private IntentFilter filter;
	private ResponseReceiver receiver;

	private boolean locked = false;
	private boolean wasRunning = false;

	private DragSortListView.DropListener onDrop =
			new DragSortListView.DropListener()
			{
				@Override
				public void drop(int from, int to)
				{
					if (from != to)
					{
						String item = listAdapter.getItem(from);
						listAdapter.remove(item);
						listAdapter.insert(item, to);
						
						LocalFile pdf = chosenPdfs.get(from);
						chosenPdfs.remove(pdf);
						chosenPdfs.add(to, pdf);
					}
				}
			};

	public DragSortController buildController(DragSortListView dslv)
	{
		DragSortController controller = new DragSortController(dslv);
		controller.setDragHandleId(R.id.drag_handle);
		controller.setSortEnabled(true);
		controller.setDragInitMode(DragSortController.ON_DOWN);

		return controller;
	}

	@Override
	protected void onSaveInstanceState(Bundle outState)
	{
		outState.putInt(ANGLE_EXTRA, rotationSpinner.getSelectedItemPosition());
		outState.putParcelableArrayList(PDF_EXTRA, (ArrayList<? extends Parcelable>) chosenPdfs);
	}

	@Override
	protected void onPause()
	{
		super.onPause();

		unregisterReceiver(receiver);

		if (locked)
		{
			wasRunning = true;
		}
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		if (wasRunning && !isMyServiceRunning(PdfRotateService.class))
		{
			rotateDone();
		}
		else if (isMyServiceRunning(PdfRotateService.class))
		{
			prepRotate();
		}
		registerReceiver(receiver, filter);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		trial = PdfRotateMain.this.getPackageName().equals(FREE_VERSION);
		if (trial)
			setContentView(R.layout.free_activity_pdf_rotate_main);
		else
			setContentView(R.layout.base_activity_pdf_rotate_main);

		listAdapter = new ArrayAdapter<String>(this, R.layout.list_item_handle_left,
				R.id.text, new ArrayList<String>());
		listView = (DragSortListView) findViewById(R.id.files_view);
		listView.setAdapter(listAdapter);

		image = (ImageView) findViewById(R.id.rotate_image);
		rotateButton = (Button) findViewById(R.id.rotate_button);

		rotationSpinner = (Spinner) findViewById(R.id.rotation_spinner);
		ArrayAdapter<RotationAngle> rotationAdapter =
				new ArrayAdapter<RotationAngle>(this, R.layout.spinner_textview, RotationAngle.values());
		rotationAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		rotationSpinner.setAdapter(rotationAdapter);

		rotationSpinner.setOnItemSelectedListener(new OnItemSelectedListener()
		{

			@Override
			public void onItemSelected(AdapterView<?> arg0, View arg1, int position, long arg3)
			{
				setImagePosition(position);
			}

			@Override
			public void onNothingSelected(AdapterView<?> arg0)
			{
			}
		});

		filter = new IntentFilter(ACTION_RESP);
		filter.addCategory(Intent.CATEGORY_DEFAULT);

		receiver = new ResponseReceiver();

		progress = (ProgressBar) findViewById(R.id.progress_bar);
		progressText = (TextView) findViewById(R.id.progress_text);

		if (isMyServiceRunning(PdfRotateService.class))
		{
			prepRotate();
		}
		if (savedInstanceState != null)
		{
			rotationSpinner.setSelection(savedInstanceState.getInt(ANGLE_EXTRA, 0));
			List<LocalFile> pdfs = savedInstanceState.getParcelableArrayList(PDF_EXTRA);
			addFilesToList(pdfs);
		}
		else if (!isMyServiceRunning(PdfRotateService.class))
		{
			DisplayCorruptAlert();
		}
		
		DragSortController cont = buildController(listView);
		
		listView.setDropListener(onDrop);
		listView.setFloatViewManager(cont);
		listView.setDragEnabled(true);
		listView.setOnTouchListener(cont);
	}

	private void setImagePosition(int position)
	{
		switch (position)
		{
			case 0:
				image.setImageResource(R.drawable.cw90);
				break;
			case 1:
				image.setImageResource(R.drawable.ccw90);
				break;
			case 2:
				image.setImageResource(R.drawable.rotate);
				break;
			case 3:
				image.setImageResource(R.drawable.merge);
				break;
		}
		if (position == 3)
		{
			rotateButton.setText("Merge!");
		}
		else
		{
			rotateButton.setText("Rotate!");
		}
	}

	private boolean isMyServiceRunning(Class<? extends Service> myService)
	{
		ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
		for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
		{
			if (myService.getName().equals(service.service.getClassName()))
			{
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Inflate the menu; this adds items to the action bar if it is present.
		// getMenuInflater().inflate(R.menu.pdf_rotate_main, menu);
		return true;
	}

	public void addFiles(View view)
	{
		if (!locked)
		{
			Intent intent = new Intent(this, FileChooserActivity.class);
			intent.putExtra(FileChooserActivity._RegexFilenameFilter, PDF_REGEX);
			intent.putExtra(FileChooserActivity._MultiSelection, true);
			intent.putExtra(FileChooserActivity._Rootpath, Environment.getExternalStorageDirectory().toString());
			startActivityForResult(intent, REQ_CHOOSEFILE);
		}
	}

	public void doRotate(View view)
	{
		if (!locked && chosenPdfs.size() > 0)
		{
			prepRotate();

			int angle = ((RotationAngle) rotationSpinner.getSelectedItem()).angle;
			Intent intent = new Intent(this, PdfRotateService.class);
			intent.putExtra(ANGLE_EXTRA, angle)
					.putParcelableArrayListExtra(PDF_EXTRA, (ArrayList<? extends Parcelable>) chosenPdfs);

			/*
			 * if (isUsbEnabled()) { DisplayCorruptAlert(); }
			 */
			startService(intent);
		}
	}

	public void clearList(View view)
	{
		if (!locked)
		{
			chosenPdfs.clear();
			listAdapter.clear();
			listAdapter.notifyDataSetChanged();
		}
	}

	private void DisplayCorruptAlert()
	{
		new AlertDialog.Builder(this)
				.setTitle("Warning")
				.setMessage(WARNING_STRING)
				.setPositiveButton("Ok", new DialogInterface.OnClickListener()
				{
					public void onClick(DialogInterface dialog, int which)
					{

					}
				})
				.show();
	}

	/*
	 * private boolean isUsbEnabled() { String usbEnabled;
	 * 
	 * usbEnabled = Environment.getExternalStorageState();
	 * 
	 * return usbEnabled.equals(Environment.MEDIA_SHARED); }
	 */

	private void prepRotate()
	{
		locked = true;
		progress.setVisibility(View.VISIBLE);
		progressText.setVisibility(View.VISIBLE);
		listView.setVisibility(View.INVISIBLE);
	}

	private void rotateDone()
	{
		chosenPdfs.clear();
		listAdapter.clear();
		progress.setVisibility(View.INVISIBLE);
		progressText.setVisibility(View.INVISIBLE);
		listView.setVisibility(View.VISIBLE);
		listAdapter.notifyDataSetChanged();
		locked = false;
	}

	private class ResponseReceiver extends BroadcastReceiver
	{

		@Override
		public void onReceive(Context context, Intent intent)
		{
			rotateDone();
		}

	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		switch (requestCode)
		{
			case REQ_CHOOSEFILE:
				if (resultCode == RESULT_OK)
				{
					@SuppressWarnings("unchecked")
					List<LocalFile> selected = (List<LocalFile>)
							data.getSerializableExtra(FileChooserActivity._Results);

					addFilesToList(selected);
				}
				break;
		}
	}

	private void addFilesToList(List<LocalFile> files)
	{
		for (LocalFile file : files)
		{
			if (file.isDirectory())
				continue;

			boolean doesContain = false;

			for (int i = 0; i < chosenPdfs.size(); ++i)
			{
				if (file.toString().equals(chosenPdfs.get(i).toString()))
				{
					doesContain = true;
					break;
				}
			}
			if (!doesContain)
			{
				chosenPdfs.add(file);
				listAdapter.add(file.getName());
			}
		}
		listAdapter.notifyDataSetChanged();

	}
}
