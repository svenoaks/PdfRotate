package com.smp.pdfrotator;

import group.pals.android.lib.ui.filechooser.io.localfile.LocalFile;
import static com.smp.pdfrotator.PdfRotateService.trial;

import java.io.Closeable;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.BadPdfFormatException;
import com.lowagie.text.pdf.BaseFont;
import com.lowagie.text.pdf.PdfContentByte;
import com.lowagie.text.pdf.PdfCopy;
import com.lowagie.text.pdf.PdfDictionary;
import com.lowagie.text.pdf.PdfGState;
import com.lowagie.text.pdf.PdfImportedPage;
import com.lowagie.text.pdf.PdfName;
import com.lowagie.text.pdf.PdfNumber;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.PdfStamper;

import static com.smp.pdfrotator.Constants.*;

import android.util.Log;

class PdfHandler
{
	static int rotatePdfs(List<LocalFile> pdfs, int angle)
	{
		int badFiles = 0;
		for (LocalFile file : pdfs)
		{
			if (!rotatePdf(file, angle))
				++badFiles;
		}
		return badFiles;
	}

	static boolean rotatePdf(LocalFile inFile, int angle)
	{
		PdfReader reader = null;
		PdfStamper stamper = null;

		LocalFile outFile = getGoodFile(inFile, ROTATE_SUFFIX);

		boolean worked = true;

		try
		{
			reader = unlockPdf(new PdfReader(inFile.toString()));
			stamper = new PdfStamper(reader, new FileOutputStream(outFile));

			int i = FIRST_PAGE;
			int l = reader.getNumberOfPages();

			for (; i <= l; ++i)
			{
				int desiredRot = angle;
				PdfDictionary pageDict = reader.getPageN(i);

				PdfNumber rotation = pageDict.getAsNumber(PdfName.ROTATE);

				if (rotation != null)
				{
					desiredRot += rotation.intValue();
					desiredRot %= 360;
				}

				pageDict.put(PdfName.ROTATE, new PdfNumber(desiredRot));

				// if (trial)
				// addWatermark(reader, stamper, i);
			}
		}
		catch (IOException e)
		{
			worked = false;
			Log.w("Rotate", "Caught IOException in rotate");
			e.printStackTrace();
		}
		catch (DocumentException e)
		{
			worked = false;
			Log.w("Rotate", "Caught DocumentException in rotate");
			e.printStackTrace();
		}

		finally
		{
			boolean z = closeQuietly(stamper);
			boolean y = closeQuietly(reader);

			if (!(y && z))
				worked = false;
		}

		cleanUpFiles(inFile, outFile, worked);

		return worked;
	}

	private static void cleanUpFiles(LocalFile inFile, LocalFile outFile, Boolean worked)
	{
		if (worked)
		{
			if (!trial)
			{
				inFile.delete();
				outFile.renameTo(inFile);
			}
		}
		else
		{
			outFile.delete();
		}
	}

	static boolean closeQuietly(Object resource)
	{
		try
		{
			if (resource != null)
			{
				if (resource instanceof PdfReader)
					((PdfReader) resource).close();
				else if (resource instanceof PdfStamper)
					((PdfStamper) resource).close();
				else if (resource instanceof PdfCopy)
					((PdfCopy) resource).close();
				else if (resource instanceof Document)
					((Document) resource).close();
				else if (resource instanceof Closeable)
					((Closeable) resource).close();
				else
					return false;
				return true;
			}
		}
		catch (Exception ex)
		{
			Log.w("Exception during Resource.close()", ex);
			ex.printStackTrace();
		}
		return false;
	}

	public static LocalFile getGoodFile(LocalFile inFile, String suffix)
	{
		@SuppressWarnings("unused")
		String outString = inFile.getParent() + DIRECTORY_SEPARATOR +
				removeExtension(inFile.getName()) + suffix + getExtension(inFile.getName());

		LocalFile outFile = new LocalFile(inFile.getParent() + DIRECTORY_SEPARATOR +
				removeExtension(inFile.getName()) + suffix + getExtension(inFile.getName()));

		int n = 1;
		while (outFile.isFile())
		{
			outFile = new LocalFile(inFile.getParent() + DIRECTORY_SEPARATOR +
					removeExtension(inFile.getName()) + suffix + n + getExtension(inFile.getName()));
			++n;
		}
		return outFile;
	}

	/**
	 * Remove the file extension from a filename, that may include a path.
	 * 
	 * e.g. /path/to/myfile.jpg -> /path/to/myfile
	 */
	public static String removeExtension(String filename)
	{
		if (filename == null)
		{
			return null;
		}

		int index = indexOfExtension(filename);

		if (index == -1)
		{
			return filename;
		}
		else
		{
			return filename.substring(0, index);
		}
	}

	/**
	 * Return the file extension from a filename, including the "."
	 * 
	 * e.g. /path/to/myfile.jpg -> .jpg
	 */
	public static String getExtension(String filename)
	{
		if (filename == null)
		{
			return null;
		}

		int index = indexOfExtension(filename);

		if (index == -1)
		{
			return filename;
		}
		else
		{
			return filename.substring(index);
		}
	}

	public static int indexOfExtension(String filename)
	{

		if (filename == null)
		{
			return -1;
		}

		// Check that no directory separator appears after the
		// EXTENSION_SEPARATOR
		int extensionPos = filename.lastIndexOf(EXTENSION_SEPARATOR);

		int lastDirSeparator = filename.lastIndexOf(DIRECTORY_SEPARATOR);

		if (lastDirSeparator > extensionPos)
		{
			return -1;
		}

		return extensionPos;
	}

	public static int MergePdfs(List<LocalFile> pdfs)
	{
		LocalFile outFile = getGoodFile(pdfs.get(0), "-MERGED");
		int badFiles = 0;
		Document doc = null;
		PdfCopy writer = null;

		try
		{
			doc = new Document();
			writer = new PdfCopy(doc, new FileOutputStream(outFile));
			doc.open();

			PdfContentByte content = writer.getDirectContent();

			for (LocalFile file : pdfs)
			{
				if (!merge(file, doc, writer))
					++badFiles;
			}

		}
		catch (IOException e)
		{
			e.printStackTrace();
			return MERGE_FAILED;
		}
		catch (DocumentException e)
		{
			e.printStackTrace();
			return MERGE_FAILED;
		}
		finally
		{
			boolean z = closeQuietly(doc);
			boolean y = closeQuietly(writer);

			if (!(y && z))
				return MERGE_FAILED;
		}
		return badFiles;
	}

	private static boolean merge(LocalFile file, Document doc, PdfCopy writer)
	{
		final int PAGE_ONE = 1;
		PdfReader reader = null;
		try
		{
			reader = unlockPdf(new PdfReader(file.toString()));
			int i = PAGE_ONE;
			int l = reader.getNumberOfPages();

			for (; i <= l; ++i)
			{
				doc.setPageSize(reader.getPageSizeWithRotation(i));
				doc.newPage();

				PdfImportedPage importedPage = writer.getImportedPage(reader, i);

				writer.addPage(importedPage);
			}
		}
		catch (IOException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (IllegalArgumentException e)
		{
			e.printStackTrace();
			return false;
		}
		catch (BadPdfFormatException e)
		{
			e.printStackTrace();
			return false;
		}
		finally
		{
			if (!closeQuietly(reader))
				return false;
		}
		return true;
	}

	public static PdfReader unlockPdf(PdfReader reader)
	{
		if (reader == null)
		{
			return reader;
		}
		try
		{
			Field f = reader.getClass().getDeclaredField("encrypted");
			f.setAccessible(true);
			f.set(reader, false);
		}
		catch (Exception e)
		{ // ignore
		}
		return reader;
	}
}
