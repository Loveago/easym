import { createApp } from './app';
import { env } from './config/env';
import { logger } from './utils/logger';

const app = createApp();

if (require.main === module) {
  app.listen(env.PORT, () => {
    logger.info(`EasyMoMo backend listening on port ${env.PORT}`, {
      env: env.NODE_ENV,
    });
  });
}

export default app;
