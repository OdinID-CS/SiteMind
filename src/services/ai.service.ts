import { GoogleGenerativeAI } from '@google/generative-ai';
import dotenv from 'dotenv';
import fs from 'fs';
import { StructuredReportData } from '../models/report.model';

dotenv.config();

const apiKey = process.env.GEMINI_API_KEY;

export class AIService {
  /**
   * Transcribe audio file to text.
   * Utilizes Gemini's multimodal capabilities to directly transcribe spoken dictation.
   */
  public static async transcribeAudio(filePath: string, mimeType: string): Promise<string> {
    if (!apiKey) {
      throw new Error('GEMINI_API_KEY environment variable is not defined.');
    }

    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({ model: 'gemini-3.5-flash' });

    // Read the file and convert to base64
    const audioBuffer = fs.readFileSync(filePath);
    const audioBase64 = audioBuffer.toString('base64');

    const prompt = 'You are an expert technical field transcriber. Listen to this site dictation audio carefully and provide a verbatim text transcription. Include every site details, inspector observations, safety items, and progress updates exactly as mentioned.';

    const response = await model.generateContent([
      {
        inlineData: {
          mimeType,
          data: audioBase64
        }
      },
      prompt
    ]);

    const transcription = response.response.text();
    if (!transcription) {
      throw new Error('Failed to generate transcription. Gemini returned an empty response.');
    }

    return transcription;
  }

  /**
   * Structures a messy, unorganized transcript text into a strict JSON payload containing:
   * Metadata, Executive Summary, Progress Updates, and Safety Hazards.
   */
  public static async structureTranscription(transcriptionText: string): Promise<StructuredReportData> {
    if (!apiKey) {
      throw new Error('GEMINI_API_KEY environment variable is not defined.');
    }

    const genAI = new GoogleGenerativeAI(apiKey);
    const model = genAI.getGenerativeModel({
      model: 'gemini-3.5-flash',
      generationConfig: {
        responseMimeType: 'application/json'
      }
    });

    const systemPrompt = `
      You are a Principal Systems Engineer, Technical Writer, and Field Safety Auditor.
      Your task is to convert unstructured site dictation transcriptions into clean, valid JSON reports.
      You must follow this schema strictly:
      {
        "metadata": {
          "siteName": "Name of construction or field site",
          "inspectorName": "Name of the dictating inspector",
          "date": "Date of inspection in YYYY-MM-DD or readable format",
          "weather": "Weather conditions mentioned"
        },
        "executiveSummary": "A concise professional summary of current status, weather impacts, and general outlook.",
        "progressUpdates": [
          {
            "area": "Specific section/area of work e.g. Foundation, Framing",
            "status": "Current status string",
            "completionPercentage": 0 to 100,
            "details": "Elaborative details about the progress"
          }
        ],
        "safetyHazards": [
          {
            "hazardType": "Description of hazard e.g. Exposed wiring",
            "severity": "LOW" | "MEDIUM" | "HIGH",
            "mitigationAction": "Step taken or recommended to resolve"
          }
        ]
      }
    `;

    const userPrompt = `
      Analyze this transcription text and generate the JSON site report:
      """
      ${transcriptionText}
      """
    `;

    const response = await model.generateContent({
      contents: [
        { role: 'user', parts: [{ text: userPrompt }] }
      ],
      generationConfig: {
        responseMimeType: 'application/json',
      },
      systemInstruction: systemPrompt
    });

    const resultText = response.response.text();
    if (!resultText) {
      throw new Error('AI Service returned an empty response during formatting.');
    }

    try {
      return JSON.parse(resultText) as StructuredReportData;
    } catch (parseError: any) {
      throw new Error(`Failed to parse AI structured response as JSON: ${parseError.message}. Text received: ${resultText}`);
    }
  }
}
