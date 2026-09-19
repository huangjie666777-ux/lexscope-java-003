export class ParseError extends Error {
  constructor(code, message, completed = []) {
    super(message);
    this.name = 'ParseError';
    this.code = code;
    this.completed = completed;
  }
}
