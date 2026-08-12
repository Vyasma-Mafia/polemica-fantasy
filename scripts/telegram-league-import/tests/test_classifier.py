import unittest

from telegram_import.classifier import classify, normalize


class ClassifierTest(unittest.TestCase):
    def test_nfkc_and_casefold(self):
        self.assertEqual(normalize("  ЗЛ １２.０８ в 20:00 "), "зл 12.08 в 20:00")

    def test_announcement_requires_league_date_and_time(self):
        self.assertEqual(classify("#анонс_зл 12.08, начало 20:00").kind, "ANNOUNCEMENT")
        self.assertEqual(classify("#анонс_лп Дата: 11 августа Время: 19:00 МСК").kind, "ANNOUNCEMENT")
        self.assertEqual(classify("#анонс_лп Дата: 1 января 2027 Время: 09:00").kind, "ANNOUNCEMENT")
        self.assertEqual(classify("#анонс_зл завтра вечером").kind, "IGNORE")
        self.assertEqual(classify("#анонс_лп Дата: 32 августа Время: 19:00").kind, "IGNORE")
        self.assertEqual(classify("ЗЛ 12.08 20:00").kind, "IGNORE")

    def test_real_lp_series_34_announcement(self):
        text = """**Лига Претендентов: Серия 34.**

Дата: 11 августа
Время: 19:00 МСК.

🔼Таблица

📱 Twitch

#анонс_ЛП"""
        result = classify(text)
        self.assertEqual((result.kind, result.league), ("ANNOUNCEMENT", "ЛП"))

    def test_result_requires_game_and_winner_markers(self):
        result = classify("#результаты_лп Игра №1: победа мирных")
        self.assertEqual((result.kind, result.league), ("RESULT", "ЛП"))
        self.assertEqual(classify("#результаты_лп результаты готовы").kind, "IGNORE")

    def test_operational_posts_are_not_candidates(self):
        for text in ("Таблица ЗЛ после тура", "LIVE ЛП игра идёт", "Расписание ЗЛ 12.08 20:00", "#анонс_зл расписание скоро", "#результаты_зл Игра 1: Мафия, Дон, Шериф", "#анонс_зл #анонс_лп 12.08 20:00", "#анонс_зл #результаты_зл Игра 1: победа мафии 12.08 20:00"):
            with self.subTest(text=text):
                self.assertEqual(classify(text).kind, "IGNORE")


if __name__ == "__main__":
    unittest.main()
