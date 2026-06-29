import { Router } from 'express';
import { ReportController } from '../controllers/report.controller';
import { uploadHandler } from '../middleware/multer';

const router = Router();
const controller = new ReportController();

// Create Field Report from dictation (Accepts file upload with fieldname 'audio')
router.post('/', uploadHandler.single('audio'), controller.createReport);

// Get all reports
router.get('/', controller.listReports);

// Get report status and data
router.get('/:id', controller.getReportStatus);

// Download completed docx
router.get('/:id/download', controller.downloadReportDocx);

export default router;
