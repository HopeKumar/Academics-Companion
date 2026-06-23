import { Injectable } from '@angular/core';
import { SummaryService } from './summary.service';
import { FlashcardService } from './flashcard.service';
import { MindMapService } from './mindmap.service';
import { QuizService } from './quiz.service';
import { DiscussionService } from './discussion.service';
import { PodcastService } from './podcast.service';
import { UploadService } from './upload.service';
import { Observable, forkJoin, timer, of, throwError } from 'rxjs';
import { catchError, map, switchMap, takeWhile, tap, timeout } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class OrchestrationService {
  constructor(
    private summaryService: SummaryService,
    private flashcardService: FlashcardService,
    private mindmapService: MindMapService,
    private quizService: QuizService,
    private discussionService: DiscussionService,
    private podcastService: PodcastService,
    private uploadService: UploadService
  ) {}

  triggerGeneration(sourceId: string, topic: string = 'Core Concepts'): Observable<any> {
    const generateSummary$ = this.summaryService.generateSummary(sourceId).pipe(catchError(e => of(null)));
    const generateFlashcards$ = this.flashcardService.generateFlashcards(topic, sourceId).pipe(catchError(e => of(null)));
    const generateMindmap$ = this.mindmapService.generateMindmap(topic, sourceId).pipe(catchError(e => of(null)));
    const generateQuiz$ = this.quizService.generateAdaptiveQuiz({ concept: topic, sourceId, totalQuestions: 10 }).pipe(catchError(e => of(null)));
    const generateDiscussion$ = this.discussionService.createThread('Discussion on ' + topic, 'Discuss the main points of this document.', sourceId).pipe(catchError(e => of(null)));
    const generatePodcast$ = this.podcastService.generatePodcast('Audio Overview', sourceId).pipe(catchError(e => of(null)));

    return forkJoin([
      generateSummary$,
      generateFlashcards$,
      generateMindmap$,
      generateQuiz$,
      generateDiscussion$,
      generatePodcast$
    ]);
  }

  pollStatus(sourceId: string, timeoutMs: number = 480000, intervalMs: number = 3000): Observable<any> {
    const startTime = Date.now();
    return timer(0, intervalMs).pipe(
      switchMap(() => this.uploadService.getSourceStatus(sourceId)),
      map(res => {
        if (!res?.success || !res?.data) {
          return { status: 'FAILED' };
        }
        return res.data;
      }),
      takeWhile(data => {
        if (Date.now() - startTime > timeoutMs) {
          throw new Error('Polling timeout');
        }
        const bStatus = (data['status'] as string)?.toUpperCase();
        if (bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'READY' || bStatus === 'SUCCESS' || bStatus === 'FAILED' || bStatus === 'FAIL') {
          return false; // stop polling
        }
        return true; // continue polling
      }, true)
    );
  }
  pollJobStatus(sourceId: string, type: string, timeoutMs: number = 620000, intervalMs: number = 3000): Observable<any> {
    const startTime = Date.now();
    return timer(0, intervalMs).pipe(
      switchMap(() => this.uploadService.getJobStatus(sourceId, type).pipe(
        map(res => {
          if (res && res.success && res.data !== undefined) {
            return res.data;
          }
          return res;
        }),
        catchError(err => {
          if (err?.status === 403 || err?.status === 404) {
            return throwError(() => err);
          }
          return of({ status: 'PENDING' });
        })
      )),
      takeWhile(job => {
        if (Date.now() - startTime > timeoutMs) {
          throw new Error('Polling timeout');
        }
        if (!job) return true;
        const bStatus = (job.status as string)?.toUpperCase();
        if (bStatus === 'COMPLETED' || bStatus === 'COMPLETE' || bStatus === 'FAILED' || bStatus === 'FAIL') {
          return false;
        }
        return true;
      }, true)
    );
  }
}
