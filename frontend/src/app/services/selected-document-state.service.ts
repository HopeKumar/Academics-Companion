import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { SourceFile } from '../app'; // Importing from app.ts where it was exported

@Injectable({
  providedIn: 'root'
})
export class SelectedDocumentStateService {
  private activeDocumentSubject = new BehaviorSubject<SourceFile | null>(null);
  public activeDocument$: Observable<SourceFile | null> = this.activeDocumentSubject.asObservable();

  constructor() {}

  setActiveDocument(doc: SourceFile | null): void {
    this.activeDocumentSubject.next(doc);
  }

  getActiveDocument(): SourceFile | null {
    return this.activeDocumentSubject.getValue();
  }

  updateActiveDocumentStatus(status: 'Completed' | 'In progress' | 'Not started', progress: number): void {
    const doc = this.getActiveDocument();
    if (doc) {
      doc.status = status;
      doc.progress = progress;
      this.setActiveDocument(doc); // emit updated doc
    }
  }

  // Helper to partially update content without overwriting the whole document
  updateActiveDocumentContent(contentUpdate: Partial<SourceFile['content']>): void {
    const doc = this.getActiveDocument();
    if (doc) {
      doc.content = { ...doc.content, ...contentUpdate };
      this.setActiveDocument(doc);
    }
  }
}
