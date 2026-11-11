/* Công cụ phụ để dựng bảng phiên âm (tools/gen_vi_phon.py), KHÔNG nằm trong app.
 * Gọi eSpeak NG giống cách Piper làm: mỗi dòng vào ra một dòng "[phiên âm]{dấu câu}" cho từng mệnh đề.
 * Biên dịch (sau khi build eSpeak NG):
 *   gcc -O1 -o espeak_phonemes espeak_phonemes.c -I<espeak>/src/include <build>/src/libespeak-ng/libespeak-ng.a \
 *       <build>/src/ucd-tools/libucd.a -lm -lpthread -lstdc++
 * Chạy: ./espeak_phonemes <thư mục chứa espeak-ng-data>  (đọc từ stdin)
 */
#include <stdio.h>
#include <string.h>
#include <espeak-ng/speak_lib.h>
#include <espeak-ng/espeak_ng.h>
#define CLAUSE_INTONATION_FULL_STOP   0x00000000
#define CLAUSE_INTONATION_COMMA       0x00001000
#define CLAUSE_INTONATION_QUESTION    0x00002000
#define CLAUSE_INTONATION_EXCLAMATION 0x00003000
#define CLAUSE_TYPE_CLAUSE            0x00040000
#define CLAUSE_TYPE_SENTENCE          0x00080000
#define CLAUSE_PERIOD      (40 | CLAUSE_INTONATION_FULL_STOP   | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_COMMA       (20 | CLAUSE_INTONATION_COMMA       | CLAUSE_TYPE_CLAUSE)
#define CLAUSE_QUESTION    (40 | CLAUSE_INTONATION_QUESTION    | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_EXCLAMATION (45 | CLAUSE_INTONATION_EXCLAMATION | CLAUSE_TYPE_SENTENCE)
#define CLAUSE_COLON       (30 | CLAUSE_INTONATION_FULL_STOP   | CLAUSE_TYPE_CLAUSE)
#define CLAUSE_SEMICOLON   (30 | CLAUSE_INTONATION_COMMA       | CLAUSE_TYPE_CLAUSE)
/* piper-phonemize-like: prints each sentence's phoneme string with punctuation terminators. one input line per call. */
int main(int argc, char **argv) {
  espeak_ng_InitializePath(argv[1]);
  espeak_ng_Initialize(NULL);
  espeak_SetVoiceByName("vi");
  char line[8192];
  while (fgets(line, sizeof line, stdin)) {
    size_t n = strlen(line); while (n && (line[n-1]=='\n'||line[n-1]=='\r')) line[--n]=0;
    const char *p = line; int term = 0;
    while (p) {
      const char *ph = espeak_TextToPhonemesWithTerminator((const void **)&p, espeakCHARS_AUTO, 0x02, &term);
      int t = term & 0x000FFFFF;
      const char *tn = t==CLAUSE_PERIOD?".":t==CLAUSE_QUESTION?"?":t==CLAUSE_EXCLAMATION?"!":t==CLAUSE_COMMA?",":t==CLAUSE_COLON?":":t==CLAUSE_SEMICOLON?";":"";
      printf("[%s]{%s%s}%s", ph, tn, ((term & CLAUSE_TYPE_SENTENCE)==CLAUSE_TYPE_SENTENCE)?"S":"", p?" ":"");
    }
    printf("\n");
  }
  return 0;
}
