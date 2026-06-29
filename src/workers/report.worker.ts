import { Worker, Job } from 'bullmq';
import IORedis from 'ioredis';
import { redisConnection, reportQueueName } from '../config/queue';
import { ReportModel } from '../models/report.model';
import { AIService } from '../services/ai.service';
import { DocxService } from '../services/docx.service';

// Failure logging service as requested
class SystemLogger {
  public static logFailure(reportId: string, step: string, error: Error) {
    const timestamp = new Date().toISOString();
    console.error(`[SYSTEM MONITOR] [FAILURE] [${timestamp}] ReportId: ${reportId}, Step: ${step}, Error: ${error.message}`);
    if (error.stack) {
      console.error(error.stack);
    }
  }

  public static logInfo(reportId: string, message: string) {
    const timestamp = new Date().toISOString();
    console.log(`[SYSTEM MONITOR] [INFO] [${timestamp}] ReportId: ${reportId}, Message: ${message}`);
  }
}

export const reportWorker = new Worker(
  reportQueueName,
  async (job: Job) => {
    const { reportId, mimeType } = job.data;
    SystemLogger.logInfo(reportId, `Starting BullMQ report task processing in job: ${job.id}`);

    const report = await ReportModel.findById(reportId);
    if (!report) {
      const err = new Error(`Report not found in internal datastore.`);
      SystemLogger.logFailure(reportId, 'FETCH_REPORT_MODEL', err);
      throw err;
    }

    try {
      // Step 1: Transcribe spoken audio
      SystemLogger.logInfo(reportId, 'Status: TRANSCRIBING - Invoking AI Service');
      await ReportModel.updateStatus(reportId, 'TRANSCRIBING');
      const transcription = await AIService.transcribeAudio(report.filePath, mimeType);
      
      // Step 2: Extract structural JSON
      SystemLogger.logInfo(reportId, 'Status: AI_PROCESSING - Formatting text with Gemini structured schema');
      await ReportModel.updateStatus(reportId, 'AI_PROCESSING', { transcription });
      const structuredData = await AIService.structureTranscription(transcription);

      // Step 3: Compile document
      SystemLogger.logInfo(reportId, 'Status: GENERATING_DOCUMENT - Injecting into docx-template');
      await ReportModel.updateStatus(reportId, 'GENERATING_DOCUMENT', { structuredData });
      const outputDocxPath = await DocxService.generateDocument(structuredData, reportId);

      // Step 4: Complete
      SystemLogger.logInfo(reportId, 'Status: COMPLETED - Report processing finalized');
      await ReportModel.updateStatus(reportId, 'COMPLETED', { outputDocxPath });

      return {
        reportId,
        outputDocxPath,
        siteName: structuredData.metadata.siteName,
      };
    } catch (error: any) {
      SystemLogger.logFailure(reportId, 'WORKER_PIPELINE', error);
      await ReportModel.updateStatus(reportId, 'FAILED', { error: error.message });
      throw error; // Let BullMQ know the job failed
    }
  },
  {
    connection: new IORedis(redisConnection as any),
    concurrency: 2, // Allow 2 concurrent report extractions
  }
);

reportWorker.on('completed', (job) => {
  console.log(`[BullMQ Worker] Job ${job.id} completed successfully for report ${job.data.reportId}.`);
});

reportWorker.on('failed', (job, err) => {
  console.error(`[BullMQ Worker] Job ${job?.id} failed for report ${job?.data?.reportId}. Error: ${err.message}`);
});
