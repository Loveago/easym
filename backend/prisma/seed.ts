import { PrismaClient, BillingInterval, NetworkType } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // Seed configurable subscription plans (prices are admin-configurable in DB)
  const plans = [
    {
      code: 'MONTHLY',
      name: 'Monthly Agent Plan',
      price: '50.00',
      currency: 'GHS',
      billingInterval: BillingInterval.MONTHLY,
      durationDays: 30,
      active: true,
    },
    {
      code: 'QUARTERLY',
      name: 'Quarterly Agent Plan',
      price: '135.00',
      currency: 'GHS',
      billingInterval: BillingInterval.QUARTERLY,
      durationDays: 90,
      active: true,
    },
    {
      code: 'ANNUAL',
      name: 'Annual Agent Plan',
      price: '480.00',
      currency: 'GHS',
      billingInterval: BillingInterval.ANNUAL,
      durationDays: 365,
      active: true,
    },
  ];

  for (const plan of plans) {
    await prisma.subscriptionPlan.upsert({
      where: { code: plan.code },
      update: {},
      create: plan,
    });
  }

  // Seed default NetworkProfiles (prefixes for auto-detection, marked for verification)
  const networkProfiles = [
    {
      network: NetworkType.MTN,
      configuration: {
        displayName: 'MTN MoMo',
        prefixes: ['024', '025', '053', '054', '055', '059'],
        supportsAgentCashIn: true,
        supportsAgentCashOut: true,
        supportsCustomerLedCashOut: false,
      },
    },
    {
      network: NetworkType.AT,
      configuration: {
        displayName: 'AT Money',
        prefixes: ['026', '027', '056', '057'],
        supportsAgentCashIn: true,
        supportsAgentCashOut: false,
        supportsCustomerLedCashOut: true,
      },
    },
    {
      network: NetworkType.TELECEL,
      configuration: {
        displayName: 'Telecel Cash',
        prefixes: ['020', '050'],
        supportsAgentCashIn: true,
        supportsAgentCashOut: false,
        supportsCustomerLedCashOut: true,
      },
    },
  ];

  for (const profile of networkProfiles) {
    await prisma.networkProfile.upsert({
      where: { network: profile.network },
      update: {},
      create: profile,
    });
  }

  console.log('Seed completed: plans and network profiles initialized.');
}

main()
  .catch((err) => {
    console.error('Seed failed:', err);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
