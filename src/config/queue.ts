import { Queue, ConnectionOptions } from 'bullmq';
import IORedis from 'ioredis';
import dotenv from 'dotenv';

dotenv.config();

export const redisConnection: ConnectionOptions = {
  host: process.env.REDIS_HOST || '127.0.0.1',
  port: parseInt(process.env.REDIS_PORT || '6379', 10),
  maxRetriesPerRequest: null, // Required by BullMQ
};

export const reportQueueName = 'report-processing-queue';

export const reportQueue = new Queue(reportQueueName, {
  connection: new IORedis(redisConnection as any),
});
