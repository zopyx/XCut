declare module "fs" {
  const fs: {
    readFileSync(path: string, encoding: string): string;
  };
  export default fs;
}

declare const process: {
  argv: string[];
  stderr: { write(data: string): void };
  stdout: { write(data: string): void };
  exit(code?: number): never;
};

declare const require: any;

declare const module: { exports: any };
