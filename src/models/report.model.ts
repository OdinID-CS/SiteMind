export type ProcessingStatus = 
  | 'PENDING' 
  | 'TRANSCRIBING' 
  | 'AI_PROCESSING' 
  | 'GENERATING_DOCUMENT' 
  | 'COMPLETED' 
  | 'FAILED';

export interface StructuredReportData {
  metadata: {
    siteName: string;
    inspectorName: string;
    date: string;
    weather: string;
  };
  executiveSummary: string;
  progressUpdates: Array<{
    area: string;
    status: string;
    completionPercentage: number;
    details: string;
  }>;
  safetyHazards: Array<{
    hazardType: string;
    severity: 'LOW' | 'MEDIUM' | 'HIGH';
    mitigationAction: string;
  }>;
}

export interface FieldReport {
  id: string;
  originalFileName: string;
  filePath: string;
  status: ProcessingStatus;
  transcription?: string;
  structuredData?: StructuredReportData;
  outputDocxPath?: string;
  error?: string;
  createdAt: Date;
  updatedAt: Date;
}

export class ReportModel {
  private static reports: Map<string, FieldReport> = new Map();

  public static async create(originalFileName: string, filePath: string): Promise<FieldReport> {
    const id = Math.random().toString(36).substring(2, 15);
    const report: FieldReport = {
      id,
      originalFileName,
      filePath,
      status: 'PENDING',
      createdAt: new Date(),
      updatedAt: new Date(),
    };
    this.reports.set(id, report);
    return report;
  }

  public static async findById(id: string): Promise<FieldReport | null> {
    const report = this.reports.get(id);
    return report ? { ...report } : null;
  }

  public static async updateStatus(id: string, status: ProcessingStatus, extra: Partial<Omit<FieldReport, 'id' | 'status' | 'createdAt'>> = {}): Promise<FieldReport | null> {
    const report = this.reports.get(id);
    if (!report) return null;

    const updated: FieldReport = {
      ...report,
      ...extra,
      status,
      updatedAt: new Date(),
    };
    this.reports.set(id, updated);
    return updated;
  }

  public static async getAll(): Promise<FieldReport[]> {
    return Array.from(this.reports.values()).sort((a, b) => b.createdAt.getTime() - a.createdAt.getTime());
  }
}
