/** @type {import('jest').Config} */
module.exports = {
  preset: 'jest-preset-angular',
  // setup-jest.ts wires up TestBed via jest-preset-angular. It MUST run after
  // Jest's globals are installed, hence setupFilesAfterEnv (the previous typo
  // "setupFilesAfterEach" was silently ignored, so TestBed-using component
  // specs blew up with "Cannot read 'ngModule' of null").
  setupFilesAfterEnv: ['<rootDir>/setup-jest.ts'],
  testEnvironmentOptions: { customExportConditions: ['node'] },
  testMatch: ['<rootDir>/src/**/*.spec.ts'],
  moduleNameMapper: {
    '^@app/(.*)$': '<rootDir>/src/app/$1'
  },
  globalSetup: 'jest-preset-angular/global-setup',
  transformIgnorePatterns: ['node_modules/(?!.*\\.mjs$|@angular|rxjs)']
};
