/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-preset-angular',
  setupFilesAfterEach: ['<rootDir>/setup-jest.ts'],
  testEnvironmentOptions: { customExportConditions: ['node'] },
  testMatch: ['<rootDir>/src/**/*.spec.ts'],
  moduleNameMapper: {
    '^@app/(.*)$': '<rootDir>/src/app/$1'
  },
  globalSetup: 'jest-preset-angular/global-setup',
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$|@angular|rxjs)']
};
