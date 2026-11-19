import { describe, expect, it } from 'vitest';
import { parseAmazonProductUrl, parseRetailerProductUrl } from './product-url';
describe('Amazon India product URL parser', () => {
  it.each([
    ['https://amazon.in/dp/B0D2MW6L54', 'B0D2MW6L54'],
    ['https://www.amazon.in/Samsung-Galaxy-Smartphone-Storage/dp/B0FDKZMMYC', 'B0FDKZMMYC'],
    ['https://amazon.in/dp/B0D2MW6L54?th=1#details', 'B0D2MW6L54'],
    ['https://www.amazon.in/WZATCO-Official-Netflix-Google-Projector/dp/B0H42KDFN8/ref=pd_sbs_d_sccl_1_7/258-9384714-2049055', 'B0H42KDFN8'],
    ['https://www.amazon.in/gp/product/B0D2MW6L54/ref=ewc_pr_img_1?smid=AUCQGA5A6IVIW&psc=1', 'B0D2MW6L54'],
    ['https://www.amazon.in/HAWK-PROAUDIO-Gold-Male-Female/dp/B07D4JBCSY', 'B07D4JBCSY'],
    ['https://www.amazon.in/Spigen-Transparent-Protection-Thermoplastic-Polyurethane/dp/B0GKQFH9VG', 'B0GKQFH9VG'],
    ['https://amazon.in/gp/product/B0D7SJXCPP', 'B0D7SJXCPP'],
    ['https://www.amazon.in/gp/aw/d/B0D2MW6L54?th=1#details', 'B0D2MW6L54'],
    ['https://www.amazon.in/gP/PrOdUcT/b07d4jbcsy/ref=product_page?smid=seller', 'B07D4JBCSY']
  ])('accepts %s', (value, expectedAsin) => { const result=parseAmazonProductUrl(value); expect(result.supported).toBe(true); if(result.supported){expect(result.asin).toBe(expectedAsin);expect(result.canonicalUrl).toBe(`https://www.amazon.in/dp/${expectedAsin}`);} });
  it.each(['https://amazon.com/dp/B0D2MW6L54','https://amazon.in.attacker.com/dp/B0D2MW6L54','http://amazon.in/dp/B0D2MW6L54','https://amazon.in/s?k=x','https://amazon.in/dp/short','https://amazon.in/dp/B0D2MW6L5!','https://amazon.in/name/dp/B0D2MW6L54/extra','https://amazon.in/name/dp/B0D2MW6L54/other','not url'])('rejects %s', value => expect(parseAmazonProductUrl(value).supported).toBe(false));
  it('rejects missing URL',()=>expect(parseAmazonProductUrl()).toEqual({supported:false,reason:'missing_url'}));
});

describe('multi-retailer product URL parser', () => {
  it('identifies Flipkart using the exact pid variant and strips tracking query values', () => {
    const result = parseRetailerProductUrl('https://www.flipkart.com/product-name/p/itm1234567890?pid=MOBABCDE1234&lid=tracking');
    expect(result).toMatchObject({ supported: true, retailer: 'FLIPKART', productId: 'MOBABCDE1234', asin: null,
      canonicalUrl: 'https://www.flipkart.com/product-name/p/itm1234567890?pid=MOBABCDE1234' });
  });

  it('identifies Myntra numeric product IDs and strips tracking parameters', () => {
    const result = parseRetailerProductUrl('https://www.myntra.com/tshirts/roadster/12345678/buy?utm_source=mail');
    expect(result).toMatchObject({ supported: true, retailer: 'MYNTRA', productId: '12345678', asin: null,
      canonicalUrl: 'https://www.myntra.com/tshirts/roadster/12345678/buy' });
  });

  it.each([
    'https://flipkart.com/search?q=phone',
    'https://myntra.com/shop/men',
    'https://www.flipkart.com.attacker.example/p/itm1234567890?pid=MOBABCDE1234',
    'http://www.myntra.com/shoes/brand/12345678/buy',
    'https://www.flipkart.com/product/p/itm1234567890?pid=bad',
  ])('rejects unsupported or deceptive retailer URL %s', value => {
    expect(parseRetailerProductUrl(value).supported).toBe(false);
  });

  it.each([
    ['https://www.meesho.com/bhumi-warm-white-12-meter-decorative-42-warm-white-led-string-light-plug-for-indoor-outdoor-decorationsstring-lights-for-diy-party-home-decor-christmas-diwali-1/p/79g96n', 'MEESHO', '79g96n', 'https://www.meesho.com/bhumi-warm-white-12-meter-decorative-42-warm-white-led-string-light-plug-for-indoor-outdoor-decorationsstring-lights-for-diy-party-home-decor-christmas-diwali-1/p/79g96n'],
    ['https://www.ajio.com/u-s-polo-assn-men-tailored-fit-shirt-with-logo-embroidery/p/442466815_blue', 'AJIO', '442466815', 'https://www.ajio.com/u-s-polo-assn-men-tailored-fit-shirt-with-logo-embroidery/p/442466815_blue'],
    ['https://www.nykaafashion.com/nike-downshifter-14-men-s-road-running-shoe/p/26355138', 'NYKAA_FASHION', '26355138', 'https://www.nykaafashion.com/nike-downshifter-14-men-s-road-running-shoe/p/26355138'],
    ['https://www.nykaa.com/wishcare-ceramide-lip-balm-with-spf-50-pa-watermelon/p/13784199?productId=13784199&pps=1', 'NYKAA', '13784199', 'https://www.nykaa.com/wishcare-ceramide-lip-balm-with-spf-50-pa-watermelon/p/13784199'],
    ['https://www.croma.com/haier-m80f-190-cm-75-inch-3-star-4k-ultra-hd-mini-led-smart-google-tv-with-google-assistant-supported-2025-model-/p/319323', 'CROMA', '319323', 'https://www.croma.com/haier-m80f-190-cm-75-inch-3-star-4k-ultra-hd-mini-led-smart-google-tv-with-google-assistant-supported-2025-model-/p/319323']
  ] as const)('recognizes %s', (url, retailer, productId, canonicalUrl) => {
    expect(parseRetailerProductUrl(url)).toMatchObject({ supported: true, retailer, productId, canonicalUrl });
  });

  it('preserves AJIO URL-identified colour as a variant without mixing it into the product ID', () => {
    expect(parseRetailerProductUrl('https://www.ajio.com/u-s-polo-assn-men-tailored-fit-shirt-with-logo-embroidery/p/442466815_blue'))
      .toMatchObject({ supported: true, retailer: 'AJIO', productId: '442466815', variantId: 'blue', variantLabel: 'Blue' });
  });

  it.each([
    'https://www.tatacliq.com/commerce/product-details?skuId=199318',
    'https://www.nykaa.com.attacker.example/item/p/13784199',
    'https://www.meesho.com.attacker.example/product/p/79g96n',
    'http://www.croma.com/tv/p/319323',
    'https://www.ajio.com/search?q=shirt',
    'https://www.tataneu.com/commerce/product-details?skuId=199318',
    'https://www.nykaa.com/lip-balm/p/13784199?productId=other'
  ])('rejects unsupported or malformed added-retailer URL %s', url => {
    expect(parseRetailerProductUrl(url).supported).toBe(false);
  });
});
