import { Request, Response } from 'express';
import { ReportModel } from '../models/report.model';
import { reportQueue } from '../config/queue';
import fs from 'fs';

export class ReportController {
  /**
   * Accepts voice dictation audio file, creates database record, and enqueues processing.
   * Responds with HTTP 202 Accepted.
   */
  public createReport = async (req: Request, res: Response): Promise<void> => {
    try {
      if (!req.file) {
        res.status(400).json({ error: 'No audio dictation file uploaded. Make sure to specify an audio file under field key "audio".' });
        return;
      }

      // Create model entry
      const report = await ReportModel.create(req.file.originalname, req.file.path);

      // Enqueue job in BullMQ
      await reportQueue.add(
        'process-site-report',
        {
          reportId: report.id,
          mimeType: req.file.mimetype,
        },
        {
          attempts: 3,
          backoff: {
            type: 'exponential',
            delay: 5000,
          },
        }
      );

      res.status(202).json({
        message: 'Audio report dictation uploaded and processing has started.',
        reportId: report.id,
        status: report.status,
        checkStatusUrl: `/api/reports/${report.id}`,
      });
    } catch (err: any) {
      res.status(500).json({ error: `Failed to initiate report compilation: ${err.message}` });
    }
  };

  /**
   * Retrieves status and details of a single report.
   */
  public getReportStatus = async (req: Request, res: Response): Promise<void> => {
    try {
      const { id } = req.params;
      const report = await ReportModel.findById(id);

      if (!report) {
        res.status(404).json({ error: `Report with ID ${id} not found.` });
        return;
      }

      res.status(200).json(report);
    } catch (err: any) {
      res.status(500).json({ error: `Server error finding report: ${err.message}` });
    }
  };

  /**
   * Downloads compiled Word (.docx) document.
   */
  public downloadReportDocx = async (req: Request, res: Response): Promise<void> => {
    try {
      const { id } = req.params;
      const report = await ReportModel.findById(id);

      if (!report) {
        res.status(404).json({ error: `Report with ID ${id} not found.` });
        return;
      }

      if (report.status !== 'COMPLETED' || !report.outputDocxPath) {
        res.status(400).json({ 
          error: `Report is not ready for download. Current status is ${report.status}`,
          status: report.status 
        });
        return;
      }

      if (!fs.existsSync(report.outputDocxPath)) {
        res.status(410).json({ error: 'The compiled report document has been purged or is missing from disk.' });
        return;
      }

      res.download(report.outputDocxPath, `SiteMind-Report-${id}.docx`);
    } catch (err: any) {
      res.status(500).json({ error: `Server error downloading report: ${err.message}` });
    }
  };

  /**
   * Lists all site reports.
   */
  public listReports = async (req: Request, res: Response): Promise<void> => {
    try {
      const reports = await ReportModel.getAll();
      res.status(200).json(reports);
    } catch (err: any) {
      res.status(500).json({ error: `Server error fetching reports list: ${err.message}` });
    }
  };
}
