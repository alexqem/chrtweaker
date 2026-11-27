import unittest

from mycar import config, tweaks


class ProfilesAndTweaksTest(unittest.TestCase):
    def test_unknown_profile_falls_back_to_generic(self) -> None:
        key, prof = config.resolve_profile("unknown_model_xyz")
        self.assertEqual(key, "generic")
        self.assertFalse(prof["cvt_probe"])
        self.assertFalse(prof["verified"])

    def test_only_8nrfts_is_verified(self) -> None:
        verified = [k for k, v in config.PROFILES.items() if v["verified"]]
        self.assertEqual(verified, ["8nrfts"])

    def test_tweaks_loaded_from_shared_json(self) -> None:
        self.assertEqual(len(tweaks.TWEAKS), 17)
        abs_spec = tweaks.TWEAK_MAP["abs_inspection"]
        self.assertEqual(abs_spec.session_cmd, "1003")

    def test_cvt_221627_decode(self) -> None:
        from mycar import toyota

        self.assertIn("221627", toyota.CVT_CMDS)
        # (0x69 * 256 + 0x00) / 256 - 40 = 105 - 40 = 65.0 C
        val, cands = toyota.decode_atf("221627", [0x69, 0x00])
        self.assertEqual(val, 65.0)
        self.assertTrue(cands)


if __name__ == "__main__":
    unittest.main()
