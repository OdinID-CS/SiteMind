import express from 'express';
import dotenv from 'dotenv';
import reportRoutes from './routes/report.routes';

dotenv.config();

const app = express();
const port = process.env.PORT || 3000;

app.use(express.json());

// Main Router API
app.use('/api/reports', reportRoutes);

// Root Healthcheck
app.get('/health', (req, res) => {
  res.status(200).json({ status: 'UP', service: 'SiteMind-Backend' });
});

app.listen(port, () => {
  console.log(`[SiteMind Server] Monolith started and listening on http://localhost:${port}`);
  console.log(`[SiteMind Server] BullMQ worker initialized. Ensure your Redis instance is running at ${process.env.REDIS_HOST || '127.0.0.1'}:${process.env.REDIS_PORT || '6379'}.`);
});

export default app;
