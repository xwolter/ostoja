export const site = {
  name: 'Ostoja',
  author: 'xwolter',
  repo: 'https://github.com/xwolter/ostoja',
  versions: { windows: '1.2.9', android: '1.1.0', firefox: '1.1.7' },
};

/** Pliki z najnowszego wydania na GitHubie - nazwy bez numeru wersji, więc linki się nie starzeją. */
export const download = (file: string) => `${site.repo}/releases/latest/download/${file}`;
