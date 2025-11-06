import { BaseNode } from './base-node'

export class TestNode extends BaseNode {
  get args(): string[] {
    return ['-test=' + this.label.split(' ').join('\\ ')]
  }
}
